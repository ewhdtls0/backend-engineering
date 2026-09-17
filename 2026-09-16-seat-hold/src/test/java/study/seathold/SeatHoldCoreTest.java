package study.seathold;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import study.seathold.dto.*;
import study.seathold.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeatHoldCoreTest extends IntegrationSupport {
    @Test void holdAt0900ExpiresAt0905AndPersistsOneRow() {
        SeatHoldResponse result = service.hold(seatId, new HoldSeatRequest(100L));
        assertThat(result.holdId()).isNotNull();
        assertThat(result.seatId()).isEqualTo(seatId);
        assertThat(result.memberId()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(SeatHoldStatus.HELD);
        assertThat(result.expiresAt()).isEqualTo(LocalDateTime.parse("2026-09-16T09:05:00"));
        assertThat(holds.count()).isEqualTo(1);
        SeatHold stored = holds.findById(result.holdId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(SeatHoldStatus.HELD);
        assertThat(stored.getMemberId()).isEqualTo(100L);
        assertThat(stored.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-09-16T09:00:00"));
        assertThat(stored.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-09-16T09:05:00"));
        assertThat(activeHolds()).isEqualTo(1);
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
    }
    @Test void anotherMemberAt0904Gets409AndOriginalHoldRemains() throws Exception {
        Long a = service.hold(seatId, new HoldSeatRequest(100L)).holdId();
        time("09:04");
        mvc.perform(post("/api/seats/{id}/holds", seatId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":200}")).andExpect(status().isConflict());
        assertThat(holdStatus(a)).isEqualTo("HELD");
        assertThat(holds.findById(a).orElseThrow().getMemberId()).isEqualTo(100L);
        assertThat(holds.count()).isEqualTo(1);
        assertThat(activeHolds()).isEqualTo(1);
    }
    @Test void exactly0905AllowsNewHoldWithoutDeletingHistory() {
        Long a = service.hold(seatId, new HoldSeatRequest(100L)).holdId();
        time("09:05");
        SeatHoldResponse b = service.hold(seatId, new HoldSeatRequest(200L));
        assertThat(b.holdId()).isNotEqualTo(a);
        assertThat(b.memberId()).isEqualTo(200L);
        assertThat(b.status()).isEqualTo(SeatHoldStatus.HELD);
        assertThat(b.expiresAt()).isEqualTo(LocalDateTime.parse("2026-09-16T09:10:00"));
        assertThat(holds.existsById(a)).isTrue();
        assertThat(holds.count()).isEqualTo(2);
        assertThat(holdStatus(b.holdId())).isEqualTo("HELD");
        assertThat(activeHolds()).isEqualTo(1);
    }
    @Test void confirmAt0903CommitsBothStates() {
        Long id = heldFixture();
        time("09:03");
        SeatHoldResponse response = service.confirm(id, new ConfirmSeatHoldRequest(100L));
        assertThat(response.holdId()).isEqualTo(id);
        assertThat(response.status()).isEqualTo(SeatHoldStatus.CONFIRMED);
        assertThat(holdStatus(id)).isEqualTo("CONFIRMED");
        assertThat(seatStatus()).isEqualTo("RESERVED");
        assertThat(activeHolds()).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"09:05", "09:06"})
    void expiredConfirmFailsWithoutReservingSeat(String at) throws Exception {
        Long id = heldFixture();
        time(at);
        mvc.perform(post("/api/seat-holds/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":100}")).andExpect(status().isConflict());
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
        assertThat(holdStatus(id)).isIn("HELD", "EXPIRED");
        assertThat(activeHolds()).isZero();
    }
    @Test void reservedSeatRejectsHold() throws Exception {
        jdbc.update("update seats set status = 'RESERVED' where id = ?", seatId);
        mvc.perform(post("/api/seats/{id}/holds", seatId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":100}")).andExpect(status().isConflict());
        assertThat(seatStatus()).isEqualTo("RESERVED");
        assertThat(holds.count()).isZero();
    }
    @Test void anotherMemberCannotConfirm() throws Exception {
        Long id = heldFixture();
        mvc.perform(post("/api/seat-holds/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":200}")).andExpect(status().isForbidden());
        assertThat(holdStatus(id)).isEqualTo("HELD");
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
    }
    @Test void twoConcurrentRequestsHaveOneWinnerAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (long member : new long[]{100L, 200L}) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return mvc.perform(post("/api/seats/{id}/holds", seatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + member + "}"))
                        .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(activeHolds()).isEqualTo(1);
            assertThat(holds.count()).isEqualTo(1);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }
    @ParameterizedTest @ValueSource(strings = {"seats", "seat_holds"})
    void confirmWriteFailureRollsBackBothStates(String table) {
        Long id = heldFixture();
        time("09:03");
        faultOn(table);
        Throwable failure = catchThrowable(() -> service.confirm(id, new ConfirmSeatHoldRequest(100L)));
        assertThat(failure).as("confirm must fail on injected database write failure").isNotNull();
        // Prevent TODO or an unrelated failure from masquerading as successful rollback verification.
        assertThat(FailUpdateTrigger.fired.get())
            .as("confirm must reach the injected UPDATE fault; TODO is not rollback evidence").isPositive();
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
        assertThat(holdStatus(id)).isEqualTo("HELD");
        assertThat(holds.count()).isEqualTo(1);
    }
}

