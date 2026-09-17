package study.seathold;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;
class InfrastructureTest extends IntegrationSupport {
    @Test void twoConnectionsSeeCommittedFixtureAndInjectedTime() throws Exception {
        heldFixture();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.Callable<Long> read = () -> {
            try (var connection = jdbc.getDataSource().getConnection()) {
                barrier.await(5, java.util.concurrent.TimeUnit.SECONDS);
                try (var query = connection.prepareStatement(
                        "select count(*) from seat_holds where seat_id = ? and expires_at > ?")) {
                    query.setLong(1, seatId);
                    query.setObject(2, java.time.LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
                    try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
                }
            }
        };
        try {
            var first = pool.submit(read);
            var second = pool.submit(read);
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1L);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void contextClockAndCommittedFixturesAreUsable() {
        Long id = heldFixture();
        assertThat(holdStatus(id)).isEqualTo("HELD");
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
        assertThat(activeHolds()).isEqualTo(1);
        time("09:05");
        assertThat(activeHolds()).isZero();
        assertThat(holds.count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings = {"seats", "seat_holds"})
    void databaseFaultIsRealAndLeavesFailedStatementUnchanged(String table) {
        Long id = heldFixture();
        faultOn(table);
        String status = table.equals("seats") ? "RESERVED" : "CONFIRMED";
        assertThatThrownBy(() -> jdbc.update("update " + table + " set status = ?", status))
            .hasStackTraceContaining("TEST_CONFIRM_WRITE_FAILURE");
        assertThat(FailUpdateTrigger.fired.get()).isPositive();
        assertThat(seatStatus()).isEqualTo("AVAILABLE");
        assertThat(holdStatus(id)).isEqualTo("HELD");
    }
    @Test void invalidRequestsReturn400BeforeCallingTodo() throws Exception {
        mvc.perform(post("/api/seats/{id}/holds", seatId).contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/seat-holds/1/confirm").contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isBadRequest());
    }
}
