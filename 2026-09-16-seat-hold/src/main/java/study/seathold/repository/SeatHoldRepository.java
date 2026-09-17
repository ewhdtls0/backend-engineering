package study.seathold.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.seathold.domain.SeatHold;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {
    // TODO: 요구사항에 필요한 조회/동시성 전략을 직접 설계하세요.
    @Query("select sh from SeatHold sh where sh.seat.id = :seatId and sh.expiresAt > :now")
    List<SeatHold> findAllAvailableBySeatId(@Param("seatId") Long seatId, @Param("now") LocalDateTime now);

    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query("select sh from SeatHold sh join fetch sh.seat where sh.id = :seatHoldId and sh.status = SeatHoldStatus.HELD")
    Optional<SeatHold> findByIdWithSeat(@Param("seatHoldId") Long seatHoldId);
}

