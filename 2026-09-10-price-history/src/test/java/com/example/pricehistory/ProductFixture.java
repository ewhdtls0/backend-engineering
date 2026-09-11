package com.example.pricehistory;

import com.example.pricehistory.domain.PriceHistory;
import com.example.pricehistory.domain.Product;
import com.example.pricehistory.dto.ChangePriceRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.time.LocalDateTime;

public final class ProductFixture {
    private ProductFixture() {}

    public static Product product() {
        return new Product("학습 상품", new BigDecimal("39000"), LocalDateTime.of(2026, 9, 10, 9, 0));
    }

    public static ChangePriceRequest request(String price) {
        return new ChangePriceRequest(new BigDecimal(price), "공급가 인상");
    }

    public static PriceHistory history(Product product, String previousPrice, String changedPrice, LocalDateTime changedAt) {
        PriceHistory history = newPriceHistory();
        ReflectionTestUtils.setField(history, "product", product);
        ReflectionTestUtils.setField(history, "previousPrice", new BigDecimal(previousPrice));
        ReflectionTestUtils.setField(history, "changedPrice", new BigDecimal(changedPrice));
        ReflectionTestUtils.setField(history, "reason", "fixture");
        ReflectionTestUtils.setField(history, "changedAt", changedAt);
        return history;
    }

    private static PriceHistory newPriceHistory() {
        try {
            Constructor<PriceHistory> constructor = PriceHistory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("PriceHistory fixture를 만들 수 없습니다.", exception);
        }
    }
}
