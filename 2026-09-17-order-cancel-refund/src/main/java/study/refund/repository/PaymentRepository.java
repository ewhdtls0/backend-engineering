package study.refund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.refund.domain.Payment;

import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    @Query("select p from Payment p join fetch p.order where p.id = :paymentId")
    Optional<Payment> findByIdWithOrder(@Param("paymentId") Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.id = :paymentId")
    Optional<Payment> findByIdWithOrderForUpdate(@Param("paymentId") Long paymentId);
}
