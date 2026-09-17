package study.seathold;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import study.seathold.domain.*;
import study.seathold.repository.*;
import study.seathold.service.SeatHoldService;
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationSupport.TimeConfig.class)
abstract class IntegrationSupport {
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired MutableClock clock;
    @Autowired SeatRepository seats;
    @Autowired SeatHoldRepository holds;
    @Autowired SeatHoldService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    Long seatId;
    @BeforeEach void setup() {
        dropFault();
        holds.deleteAllInBatch();
        seats.deleteAllInBatch();
        clock.at("2026-09-16T09:00:00");
        seatId = seats.saveAndFlush(new Seat("A-10", SeatStatus.AVAILABLE)).getId();
    }
    @AfterEach void cleanup() { dropFault(); }
    void time(String hm) { clock.at("2026-09-16T" + hm + ":00"); }
    Long heldFixture() {
        return holds.saveAndFlush(new SeatHold(seats.findById(seatId).orElseThrow(),
            100L, SeatHoldStatus.HELD, LocalDateTime.parse("2026-09-16T09:05:00"),
            LocalDateTime.parse("2026-09-16T09:00:00"))).getId();
    }
    String seatStatus() {
        return jdbc.queryForObject("select status from seats where id = ?", String.class, seatId);
    }
    String holdStatus(Long id) {
        return jdbc.queryForObject("select status from seat_holds where id = ?", String.class, id);
    }
    long activeHolds() {
        return jdbc.queryForObject(
            "select count(*) from seat_holds where seat_id = ? and status = 'HELD' and expires_at > ?",
            Long.class, seatId, LocalDateTime.ofInstant(clock.instant(), clock.getZone()));
    }
    void faultOn(String table) {
        if (!table.equals("seats") && !table.equals("seat_holds")) throw new IllegalArgumentException(table);
        FailUpdateTrigger.fired.set(0);
        jdbc.execute("CREATE TRIGGER confirm_write_fault BEFORE UPDATE ON " + table +
            " FOR EACH ROW CALL 'study.seathold.FailUpdateTrigger'");
    }
    void dropFault() { jdbc.execute("DROP TRIGGER IF EXISTS confirm_write_fault"); }
}

