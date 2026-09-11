package com.example.pricehistory;

import com.example.pricehistory.domain.PriceHistory;
import com.example.pricehistory.domain.Product;
import com.example.pricehistory.dto.PriceHistoryResponse;
import com.example.pricehistory.repository.PriceHistoryRepository;
import com.example.pricehistory.repository.ProductRepository;
import com.example.pricehistory.service.PriceHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PriceHistoryMissionTest {
    @Autowired PriceHistoryService service;
    @Autowired ProductRepository products;
    @Autowired PriceHistoryRepository histories;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        histories.deleteAll();
        products.deleteAll();
    }

    @Test
    @DisplayName("가격이 바뀌면 상품 가격과 가격 이력 한 건을 함께 저장한다")
    void changesPriceAndCreatesHistory() {
        Product product = products.save(ProductFixture.product());
        LocalDateTime before = LocalDateTime.now();

        service.changePrice(product.getId(), ProductFixture.request("42000"));

        Product changed = products.findById(product.getId()).orElseThrow();
        List<PriceHistory> saved = histories.findAll();
        assertThat(changed.getPrice()).isEqualByComparingTo("42000");
        assertThat(changed.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(saved).singleElement().satisfies(history -> {
            assertThat(history.getProduct().getId()).isEqualTo(product.getId());
            assertThat(history.getPreviousPrice()).isEqualByComparingTo("39000");
            assertThat(history.getChangedPrice()).isEqualByComparingTo("42000");
            assertThat(history.getReason()).isEqualTo("공급가 인상");
            assertThat(history.getChangedAt()).isAfterOrEqualTo(before);
        });
    }

    @Test
    @DisplayName("BigDecimal scale만 다른 동일 가격이면 상품과 이력을 바꾸지 않는다")
    void doesNotCreateHistoryForSamePrice() {
        Product product = products.save(ProductFixture.product());
        LocalDateTime originalUpdatedAt = product.getUpdatedAt();

        service.changePrice(product.getId(), ProductFixture.request("39000.00"));

        Product unchanged = products.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getPrice()).isEqualByComparingTo("39000");
        assertThat(unchanged.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(histories.findAll()).isEmpty();
    }

    @Test
    @DisplayName("여러 가격 변경 이력은 changedAt 최신순으로 반환한다")
    void returnsMultipleChangesInReverseChronologicalOrder() {
        Product product = products.save(ProductFixture.product());
        service.changePrice(product.getId(), ProductFixture.request("40000.00"));
        service.changePrice(product.getId(), ProductFixture.request("41000.00"));
        service.changePrice(product.getId(), ProductFixture.request("42000.00"));

        List<PriceHistoryResponse> response = service.getPriceHistories(product.getId(), null, null);

        assertThat(response).extracting(PriceHistoryResponse::changedPrice)
                .containsExactly(new BigDecimal("42000.00"), new BigDecimal("41000.00"), new BigDecimal("40000.00"));
        assertThat(response).extracting(PriceHistoryResponse::previousPrice)
                .containsExactly(new BigDecimal("41000.00"), new BigDecimal("40000.00"), new BigDecimal("39000.00"));
        assertThat(response).extracting(PriceHistoryResponse::changedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("기간 조건은 양 끝을 포함하며 from 또는 to만 지정할 수 있다")
    void filtersHistoriesByInclusiveDateRange() {
        Product product = products.save(ProductFixture.product());
        LocalDateTime first = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime middle = LocalDateTime.of(2026, 9, 5, 12, 0);
        LocalDateTime last = LocalDateTime.of(2026, 9, 10, 23, 59, 59);
        histories.saveAll(List.of(
                ProductFixture.history(product, "39000.00", "40000.00", first),
                ProductFixture.history(product, "40000.00", "41000.00", middle),
                ProductFixture.history(product, "41000.00", "42000.00", last)
        ));

        assertThat(service.getPriceHistories(product.getId(), first, middle))
                .extracting(PriceHistoryResponse::changedPrice)
                .containsExactly(new BigDecimal("41000.00"), new BigDecimal("40000.00"));
        assertThat(service.getPriceHistories(product.getId(), middle, null))
                .extracting(PriceHistoryResponse::changedPrice)
                .containsExactly(new BigDecimal("42000.00"), new BigDecimal("41000.00"));
        assertThat(service.getPriceHistories(product.getId(), null, first))
                .extracting(PriceHistoryResponse::changedPrice)
                .containsExactly(new BigDecimal("40000.00"));
    }

    @Test
    @DisplayName("from이 to보다 늦으면 400을 반환한다")
    void rejectsInvalidDateRange() throws Exception {
        Product product = products.save(ProductFixture.product());

        mvc.perform(get("/api/products/{productId}/price-histories", product.getId())
                        .queryParam("from", "2026-09-11T00:00:00")
                        .queryParam("to", "2026-09-10T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("가격과 사유의 형식이 잘못되면 서비스 호출 전 400을 반환한다")
    void validatesPatchRequest() throws Exception {
        Product product = products.save(ProductFixture.product());
        String tooLongReason = "x".repeat(501);

        for (String body : List.of(
                "{\"price\":-1,\"reason\":\"공급가 인상\"}",
                "{\"price\":null,\"reason\":\"공급가 인상\"}",
                "{\"price\":42000,\"reason\":\"   \"}",
                "{\"price\":42000,\"reason\":\"" + tooLongReason + "\"}",
                "not-json")) {
            mvc.perform(patch("/api/products/{productId}/price", product.getId())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/products/{productId}/price-histories", product.getId())
                        .queryParam("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 상품의 가격 변경과 이력 조회는 404를 반환한다")
    void returnsNotFoundForUnknownProduct() throws Exception {
        mvc.perform(patch("/api/products/{productId}/price", 999999L)
                        .contentType("application/json")
                        .content("{\"price\":42000,\"reason\":\"공급가 인상\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mvc.perform(get("/api/products/{productId}/price-histories", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("이력 INSERT가 실패하면 상품 가격과 updatedAt도 롤백한다")
    void rollsBackPriceWhenHistoryStorageFails() {
        Product product = products.save(ProductFixture.product());
        LocalDateTime originalUpdatedAt = product.getUpdatedAt();
        jdbc.execute("alter table price_history add constraint reject_price_history check (changed_price < 0)");

        assertThatThrownBy(() -> service.changePrice(product.getId(), ProductFixture.request("42000")))
                .isInstanceOf(DataIntegrityViolationException.class);

        Product unchanged = products.findById(product.getId()).orElseThrow();
        assertThat(unchanged.getPrice()).isEqualByComparingTo("39000");
        assertThat(unchanged.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(histories.findAll()).isEmpty();
    }
}
