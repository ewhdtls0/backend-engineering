package study.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(value = LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findById(Long id);
}
