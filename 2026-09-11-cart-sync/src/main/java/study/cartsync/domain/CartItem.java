package study.cartsync.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cart_items", uniqueConstraints =
    @UniqueConstraint(name = "uk_cart_product", columnNames = {"cart_id", "product_id"}))
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 기본 매핑은 정답이 아닙니다. fetch/cascade 등은 직접 판단하세요.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Column(nullable = false)
    private int quantity;

    protected CartItem() {}
    public CartItem(Cart cart, Product product, int quantity) {
        this.cart = cart; this.product = product; this.quantity = quantity;
    }
    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void changeQuantity(int quantity) { this.quantity = quantity; }
}
