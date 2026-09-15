package study.cartsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.cartsync.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
}
