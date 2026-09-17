package study.seathold;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;
    MutableClock() { this(new AtomicReference<>(), ZoneId.of("Asia/Seoul")); }
    private MutableClock(AtomicReference<Instant> now, ZoneId zone) { this.now = now; this.zone = zone; }
    void at(String local) { now.set(LocalDateTime.parse(local).atZone(zone).toInstant()); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new MutableClock(now, zone); }
    @Override public Instant instant() { return now.get(); }
}

