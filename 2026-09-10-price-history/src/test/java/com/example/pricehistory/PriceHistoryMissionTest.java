package com.example.pricehistory;
import com.example.pricehistory.repository.*;
import com.example.pricehistory.service.PriceHistoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@AutoConfigureMockMvc
class PriceHistoryMissionTest {
    @Autowired PriceHistoryService service;
    @Autowired ProductRepository products;
    @Autowired PriceHistoryRepository histories;
    @Autowired MockMvc mvc;

    // 테스트 자체에 @Transactional을 붙이면 서비스의 트랜잭션 누락을 숨길 수 있습니다.
    @BeforeEach void clean() { histories.deleteAll(); products.deleteAll(); }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("정상 변경: 39000 → 42000, 상품 가격/이력 이전값·변경값·사유·시간 검증")
    void normalChange() {
        // TODO: Given / When / Then을 작성하세요.
        // 정상 변경: 39000 → 42000, 상품 가격/이력 이전값·변경값·사유·시간 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("동일 가격: 39000.0과 39000.00도 이력 0건, updatedAt 불변 검증")
    void samePrice() {
        // TODO: Given / When / Then을 작성하세요.
        // 동일 가격: 39000.0과 39000.00도 이력 0건, updatedAt 불변 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("여러 번 변경 후 최신순 검증; 같은 시간일 때 정렬 기준도 결정")
    void multipleChangesLatestFirst() {
        // TODO: Given / When / Then을 작성하세요.
        // 여러 번 변경 후 최신순 검증; 같은 시간일 때 정렬 기준도 결정
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("from/to 양쪽·한쪽·없음, 경계값 및 범위 밖 제외 검증")
    void dateRange() {
        // TODO: Given / When / Then을 작성하세요.
        // from/to 양쪽·한쪽·없음, 경계값 및 범위 밖 제외 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("from > to 요청 처리 정책 및 400 응답 검증")
    void invalidRange() {
        // TODO: Given / When / Then을 작성하세요.
        // from > to 요청 처리 정책 및 400 응답 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("MockMvc로 음수/null 가격, 빈 사유, 길이 초과, 잘못된 JSON/날짜의 400 검증")
    void validation() {
        // TODO: Given / When / Then을 작성하세요.
        // MockMvc로 음수/null 가격, 빈 사유, 길이 초과, 잘못된 JSON/날짜의 400 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("PATCH와 GET의 미존재 상품 404 검증")
    void missingProduct() {
        // TODO: Given / When / Then을 작성하세요.
        // PATCH와 GET의 미존재 상품 404 검증
        fail("TODO: 시나리오 구현");
    }

    @Test @Disabled("TODO: 시나리오 구현 후 활성화")
    @DisplayName("실제 Spring 서비스 프록시 호출 중 이력 저장 실패 유도; 별도 트랜잭션에서 가격/updatedAt/이력 재조회 검증")
    void historyFailureRollsBack() {
        // TODO: Given / When / Then을 작성하세요.
        // 실제 Spring 서비스 프록시 호출 중 이력 저장 실패 유도; 별도 트랜잭션에서 가격/updatedAt/이력 재조회 검증
        fail("TODO: 시나리오 구현");
    }
}
