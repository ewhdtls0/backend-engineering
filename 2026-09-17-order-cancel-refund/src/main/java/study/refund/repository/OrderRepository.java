package study.refund.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.refund.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {}
