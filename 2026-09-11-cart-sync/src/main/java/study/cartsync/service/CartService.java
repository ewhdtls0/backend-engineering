package study.cartsync.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.cartsync.domain.Cart;
import study.cartsync.domain.CartItem;
import study.cartsync.domain.Product;
import study.cartsync.dto.CartResponse;
import study.cartsync.dto.SyncCartItemsRequest;
import study.cartsync.exception.MissionException;
import study.cartsync.repository.CartItemRepository;
import study.cartsync.repository.CartRepository;
import study.cartsync.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class CartService {
    private final CartRepository carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;

    public CartService(CartRepository carts, CartItemRepository cartItems, ProductRepository products) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.products = products;
    }

    @Transactional
    public CartResponse syncItems(Long cartId, SyncCartItemsRequest request) {

        if (request == null || request.items() == null) {
            throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
        }
        Set<Long> productIds = new HashSet<>();
        request.items()
                .forEach(item -> {
                    if (item == null || item.productId() == null || item.quantity() == null) {
                        throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
                    }
                    boolean added = productIds.add(item.productId());
                    if (!added) {
                        throw new MissionException(MissionException.Code.INVALID_REQUEST, "중복된 상품이 있습니다.");
                    }
                    if (item.quantity() <= 0)
                        throw new MissionException(MissionException.Code.INVALID_REQUEST, "상품 수량이 잘못 되었습니다.");
                });

        Cart cartEntity = carts.findById(cartId)
                .orElseThrow(() -> new MissionException(MissionException.Code.CART_NOT_FOUND, "장바구니가 존재하지 않습니다."));

        if (request.items().isEmpty()) {
            cartEntity.getItems().clear();
            return new CartResponse(
                    cartId,
                    0L,
                    BigDecimal.ZERO,
                    List.of()
            );
        }

        List<CartItem> allWithProduct = cartEntity.getItems();

        Set<Long> requestItemIds = request.items()
                .stream()
                .map(SyncCartItemsRequest.Item::productId)
                .collect(Collectors.toSet());

        Set<Long> cartItemProductIds = allWithProduct.stream()
                .map(cartItem -> cartItem.getProduct().getId())
                .collect(Collectors.toSet());

        allWithProduct.removeIf(
                cartItem -> !requestItemIds.contains(cartItem.getProduct().getId())
        );

        // 신규 상품 추가 및 요청 없는 상품 제거
        request.items().stream()
                .filter(item -> !cartItemProductIds.contains(item.productId()))
                .map(item -> {
                    Product productEntity = products.findById(item.productId())
                            .orElseThrow(() -> new MissionException(MissionException.Code.PRODUCT_NOT_FOUND, "상품이 존재하지 않습니다."));
                    return new CartItem(cartEntity, productEntity, item.quantity());
                })
                .forEach(allWithProduct::add);

        // 이미있는 상품 중 개수가 다르면 업데이트
        allWithProduct.forEach(cartItem -> {
            request.items().stream()
                    .filter(item -> item.productId().equals(cartItem.getProduct().getId()))
                    .findFirst()
                    .ifPresent(item -> {
                        if (item.quantity() != cartItem.getQuantity()) {
                            cartItem.changeQuantity(item.quantity());
                        }
                    });
        });

        AtomicInteger totalQuantity = new AtomicInteger();
        var ref = new Object() {
            BigDecimal totalPrice = BigDecimal.ZERO;
        };

        allWithProduct.forEach(cartItem -> {
            totalQuantity.addAndGet(cartItem.getQuantity());
            ref.totalPrice = ref.totalPrice.add(cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        });
        return new CartResponse(
                cartId,
                totalQuantity.get(),
                ref.totalPrice,
                allWithProduct.stream()
                        .map(cartItem -> new CartResponse.Item(
                                cartItem.getProduct().getId(),
                                cartItem.getProduct().getName(),
                                cartItem.getQuantity(),
                                cartItem.getProduct().getPrice(),
                                cartItem.getProduct().getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                            ))
                        .toList()

        );
    }
}
