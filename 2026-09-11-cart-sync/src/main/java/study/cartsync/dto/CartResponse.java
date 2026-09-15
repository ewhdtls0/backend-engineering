package study.cartsync.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long cartId, long totalQuantity, BigDecimal totalPrice,
                           List<Item> items) {
    public record Item(Long productId, String productName, int quantity,
                       BigDecimal unitPrice, BigDecimal linePrice) {}
}
