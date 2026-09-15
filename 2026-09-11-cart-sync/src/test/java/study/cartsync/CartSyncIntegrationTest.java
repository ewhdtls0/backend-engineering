package study.cartsync;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import study.cartsync.domain.*;
import study.cartsync.dto.*;
import study.cartsync.exception.MissionException;
import study.cartsync.service.CartService;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static study.cartsync.exception.MissionException.Code.*;

@SpringBootTest(properties = {
    // 테스트 fixture가 데이터를 준비하므로 앱의 data.sql에 의존하지 않습니다.
    "spring.sql.init.mode=never",
    "spring.datasource.url=jdbc:h2:mem:cart-test;DB_CLOSE_DELAY=-1",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=study.cartsync.CartSyncIntegrationTest$SqlRecorder",
    "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@AutoConfigureMockMvc
class CartSyncIntegrationTest {
    @Autowired CartService service;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    TransactionTemplate tx;
    Long cartId, otherCartId, a, b, c;

    public static class SqlRecorder implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();
        public String inspect(String sql) { SQL.add(sql); return sql; }
    }
    record Row(long id, long productId, int quantity) {}

    @BeforeEach
    void fixture() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(status -> {
            em.createQuery("delete from CartItem").executeUpdate();
            em.createQuery("delete from Cart").executeUpdate();
            em.createQuery("delete from Product").executeUpdate();
            Product pa = new Product("키보드", new BigDecimal("12000.50"));
            Product pb = new Product("마우스", new BigDecimal("3500.25"));
            Product pc = new Product("케이블", new BigDecimal("999.99"));
            em.persist(pa); em.persist(pb); em.persist(pc);
            Cart cart = new Cart(); Cart other = new Cart();
            em.persist(cart); em.persist(other);
            // 영속화 픽스처는 학습자가 선택할 cascade/편의 메서드에 의존하지 않습니다.
            em.persist(new CartItem(cart, pa, 2));
            em.persist(new CartItem(cart, pb, 3));
            em.persist(new CartItem(other, pc, 7));
            em.flush();
            cartId = cart.getId(); otherCartId = other.getId();
            a = pa.getId(); b = pb.getId(); c = pc.getId();
        });
        assertThat(rows(cartId)).extracting(Row::quantity).containsExactly(2, 3);
        resetSql();
    }
    // 테스트 자체에 @Transactional을 붙이지 않습니다. 서비스 commit/rollback 이후 DB를 읽습니다.
    List<Row> rows(Long id) {
        return jdbc.query("select id, product_id, quantity from cart_items where cart_id=? order by product_id",
            (rs, n) -> new Row(rs.getLong(1), rs.getLong(2), rs.getInt(3)), id);
    }
    SyncCartItemsRequest.Item item(Long id, int quantity) { return new SyncCartItemsRequest.Item(id, quantity); }
    SyncCartItemsRequest request(SyncCartItemsRequest.Item... items) { return new SyncCartItemsRequest(List.of(items)); }
    void resetSql() {
        SqlRecorder.SQL.clear();
        emf.unwrap(SessionFactory.class).getStatistics().clear();
    }
    void quantities(Map<Long, Integer> expected) {
        assertThat(rows(cartId)).hasSize(expected.size());
        assertThat(rows(cartId)).allSatisfy(row -> assertThat(row.quantity()).isEqualTo(expected.get(row.productId())));
        assertThat(rows(otherCartId)).extracting(Row::quantity).containsExactly(7);
        tx.executeWithoutResult(status -> {
            Cart loaded = em.find(Cart.class, cartId);
            assertThat(loaded.getItems()).hasSize(expected.size());
            assertThat(loaded.getItems()).allSatisfy(i -> {
                assertThat(i.getCart().getId()).isEqualTo(cartId);
                assertThat(i.getQuantity()).isEqualTo(expected.get(i.getProduct().getId()));
            });
        });
    }
    void rejectsWithoutChanges(SyncCartItemsRequest request, MissionException.Code code) {
        List<Row> before = rows(cartId), otherBefore = rows(otherCartId);
        Throwable error = catchThrowable(() -> service.syncItems(cartId, request));
        // UOE 등 임의의 예외만 발생해도 통과하는 테스트가 아닙니다.
        assertThat(error).isInstanceOf(MissionException.class);
        assertThat(((MissionException) error).getCode()).isEqualTo(code);
        assertThat(rows(cartId)).containsExactlyElementsOf(before);
        assertThat(rows(otherCartId)).containsExactlyElementsOf(otherBefore);
    }

    @Test void fixtureIsCommittedAndReloadable() { quantities(Map.of(a, 2, b, 3)); }

    @Test void changesExistingQuantityAndKeepsRowIds() {
        List<Row> before = rows(cartId);
        service.syncItems(cartId, request(item(a, 5), item(b, 3)));
        quantities(Map.of(a, 5, b, 3));
        assertThat(rows(cartId)).extracting(Row::id).containsExactlyElementsOf(before.stream().map(Row::id).toList());
    }

    @Test void insertsUpdatesAndDeletesTogether() {
        long retainedId = rows(cartId).getFirst().id();
        service.syncItems(cartId, request(item(a, 4), item(c, 2)));
        quantities(Map.of(a, 4, c, 2));
        assertThat(rows(cartId).getFirst().id()).isEqualTo(retainedId);
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id=? and product_id=?", Long.class, cartId, b)).isZero();
    }

    @Test void emptyRequestDeletesDatabaseRowsAndReturnsZero() {
        CartResponse response = service.syncItems(cartId, request());
        quantities(Map.of());
        assertThat(response.cartId()).isEqualTo(cartId);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalQuantity()).isZero();
        assertThat(response.totalPrice()).isEqualByComparingTo("0");
    }

    @Test void duplicateProductRejectsAndPreservesState() {
        rejectsWithoutChanges(request(item(a, 9), item(c, 1), item(a, 8)), INVALID_REQUEST);
    }

    @Test void missingProductAfterValidChangesPreservesState() {
        rejectsWithoutChanges(request(item(a, 9), item(c, 1), item(Long.MAX_VALUE, 1)), PRODUCT_NOT_FOUND);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1})
    void invalidQuantityAfterValidChangesPreservesState(int quantity) {
        rejectsWithoutChanges(request(item(a, 9), item(c, 1), item(b, quantity)), INVALID_REQUEST);
    }

    static java.util.stream.Stream<SyncCartItemsRequest> nullShapes() {
        return java.util.stream.Stream.of(
            new SyncCartItemsRequest(null),
            new SyncCartItemsRequest(Collections.singletonList(null)),
            new SyncCartItemsRequest(List.of(new SyncCartItemsRequest.Item(null, 1))),
            new SyncCartItemsRequest(List.of(new SyncCartItemsRequest.Item(1L, null)))
        );
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("nullShapes")
    void nullShapesRejectAndPreserveState(SyncCartItemsRequest invalid) {
        // 실제 존재하는 상품 ID를 써서 null 수량 검증이 없는 상품 오류에 가려지지 않게 합니다.
        if (invalid.items() != null && invalid.items().getFirst() != null
                && invalid.items().getFirst().quantity() == null) {
            invalid = new SyncCartItemsRequest(List.of(new SyncCartItemsRequest.Item(a, null)));
        }
        rejectsWithoutChanges(invalid, INVALID_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"items\":null}", "{\"items\":[null]}",
        "{\"items\":[{\"productId\":null,\"quantity\":1}]}",
        "{\"items\":[{\"productId\":1,\"quantity\":null}]}"})
    void httpRejectsNullShapes(String body) throws Exception {
        List<Row> before = rows(cartId);
        mvc.perform(put("/api/carts/{id}/items", cartId).contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(rows(cartId)).containsExactlyElementsOf(before);
    }

    @Test void responseContainsExactTotalsAndItemDetails() {
        CartResponse response = service.syncItems(cartId, request(item(a, 3), item(b, 2), item(c, 4)));
        quantities(Map.of(a, 3, b, 2, c, 4));
        assertThat(response.cartId()).isEqualTo(cartId);
        assertThat(response.totalQuantity()).isEqualTo(9);
        assertThat(response.totalPrice()).isEqualByComparingTo("47001.96");
        assertThat(response.items()).extracting(CartResponse.Item::productId).containsExactlyInAnyOrder(a, b, c);
        assertItem(response, a, "키보드", 3, "12000.50", "36001.50");
        assertItem(response, b, "마우스", 2, "3500.25", "7000.50");
        assertItem(response, c, "케이블", 4, "999.99", "3999.96");
    }
    void assertItem(CartResponse response, Long id, String name, int quantity, String price, String line) {
        var value = response.items().stream().filter(i -> i.productId().equals(id)).findFirst().orElseThrow();
        assertThat(value.productName()).isEqualTo(name);
        assertThat(value.quantity()).isEqualTo(quantity);
        assertThat(value.unitPrice()).isEqualByComparingTo(price);
        assertThat(value.linePrice()).isEqualByComparingTo(line);
    }

    @Test void missingCartFailsWithoutChangingExistingCart() {
        List<Row> before = rows(cartId);
        Throwable error = catchThrowable(() -> service.syncItems(Long.MAX_VALUE, request(item(a, 1))));
        assertThat(error).isInstanceOf(MissionException.class);
        assertThat(((MissionException) error).getCode()).isEqualTo(CART_NOT_FOUND);
        assertThat(rows(cartId)).containsExactlyElementsOf(before);
    }

    @Test void unchangedRequestPerformsNoCartItemWrites() {
        List<Row> before = rows(cartId);
        service.syncItems(cartId, request(item(b, 3), item(a, 2)));
        assertThat(SqlRecorder.SQL.stream().map(String::toLowerCase)
            .filter(sql -> sql.contains("cart_items"))
            .filter(sql -> sql.matches("(?s).*\\b(insert|update|delete|merge)\\b.*"))).isEmpty();
        assertThat(rows(cartId)).containsExactlyElementsOf(before);
        quantities(Map.of(a, 2, b, 3));
    }

    @Test void thirtyProductsExposeSelectCountWithoutPrescribingStrategy() {
        List<SyncCartItemsRequest.Item> items = tx.execute(status -> IntStream.range(0, 30).mapToObj(n -> {
            Product p = new Product("상품" + n, new BigDecimal("10.00"));
            em.persist(p);
            return item(p.getId(), 1);
        }).toList());
        resetSql();
        CartResponse response = service.syncItems(cartId, new SyncCartItemsRequest(items));
        long selects = SqlRecorder.SQL.stream().map(String::toLowerCase).filter(sql -> sql.stripLeading().startsWith("select")).count();
        System.out.println("OBSERVATION: 30 products, Hibernate SELECT statements = " + selects);
        assertThat(response.items()).hasSize(30);
        assertThat(response.totalQuantity()).isEqualTo(30);
        assertThat(response.totalPrice()).isEqualByComparingTo("300.00");
        assertThat(rows(cartId)).extracting(Row::productId).containsExactlyInAnyOrderElementsOf(items.stream().map(SyncCartItemsRequest.Item::productId).toList());
    }

    @Test void httpRejectsInvalidQuantityBeforeService() throws Exception {
        List<Row> before = rows(cartId);
        mvc.perform(put("/api/carts/{id}/items", cartId).contentType("application/json")
            .content("{\"items\":[{\"productId\":" + a + ",\"quantity\":0}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(rows(cartId)).containsExactlyElementsOf(before);
    }

    @Test void httpPutReturnsContract() throws Exception {
        mvc.perform(put("/api/carts/{id}/items", cartId).contentType("application/json")
            .content("{\"items\":[{\"productId\":" + a + ",\"quantity\":2}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cartId").value(cartId))
            .andExpect(jsonPath("$.totalQuantity").value(2))
            .andExpect(jsonPath("$.totalPrice").value(24001.00))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].productId").value(a))
            .andExpect(jsonPath("$.items[0].productName").value("키보드"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(12000.50))
            .andExpect(jsonPath("$.items[0].linePrice").value(24001.00));
        quantities(Map.of(a, 2));
    }
}
