package com.example.pricehistory;
import com.example.pricehistory.domain.Product;
import com.example.pricehistory.dto.ChangePriceRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class ProductFixture {
    private ProductFixture() {}
    public static Product product() {
        return new Product("학습 상품", new BigDecimal("39000"), LocalDateTime.of(2026, 9, 10, 9, 0));
    }
    public static ChangePriceRequest request(String price) {
        return new ChangePriceRequest(new BigDecimal(price), "공급가 인상");
    }
}
