package study.cartsync.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "carts")
public class Cart {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // TODO: 요구사항에 맞는 연관관계 생명주기 및 조회 전략을 판단하세요.
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    public Cart() {}
    public Long getId() { return id; }
    public List<CartItem> getItems() { return items; }
    // 필요하다면 양방향 연관관계 편의 메서드를 설계하세요.
    public void setItems(List<CartItem> cartItems) {
        this.items = cartItems;
    }
}
