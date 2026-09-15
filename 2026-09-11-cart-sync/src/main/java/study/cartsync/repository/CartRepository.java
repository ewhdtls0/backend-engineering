package study.cartsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.cartsync.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
}
