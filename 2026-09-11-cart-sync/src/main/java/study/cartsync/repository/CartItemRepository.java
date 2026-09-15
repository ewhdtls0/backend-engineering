package study.cartsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import study.cartsync.domain.CartItem;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("select ci from CartItem ci join fetch ci.product where ci.cart.id = :cartId")
    List<CartItem> findAllWithProduct(@Param("cartId") Long cartId);
}
