package study.seathold.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.seathold.domain.Seat;

import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    // TODO: 요구사항에 필요한 조회/동시성 전략을 직접 설계하세요.
    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") Long id);
}

