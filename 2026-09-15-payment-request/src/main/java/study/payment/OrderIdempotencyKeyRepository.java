package study.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderIdempotencyKeyRepository extends JpaRepository<OrderIdempotencyKey, Long> {
    Optional<OrderIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
    void deleteByIdempotencyKey(String idempotencyKey);
}
