# 오늘의 백엔드 미션: 장바구니 항목 동기화

Java 21 · Spring Boot 3.5.16 · Gradle 8.14.3 · Web · JPA · Validation · H2

## 실제 서비스 상황

여러 화면에서 장바구니를 편집한 클라이언트가 최종 상품 목록을 서버에 전송합니다.
서버는 현재 데이터와 비교해 필요한 항목만 수정해야 합니다. 매번 전체 삭제 후 재생성하면
기존 항목의 식별자가 사라지고 불필요한 SQL이 증가합니다.

## 실행

IntelliJ에서 이 폴더를 독립 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 Java 21로 지정합니다.
Gradle wrapper가 포함되어 있으므로 Gradle을 따로 설치할 필요는 없습니다. 최초 실행에는 의존성 다운로드가 필요합니다.

```powershell
.\gradlew.bat testClasses
.\gradlew.bat test
.\gradlew.bat bootRun
```

macOS/Linux에서는 `./gradlew test`를 사용합니다. 실행 권한이 없으면 `sh gradlew test`도 가능합니다.
현재 syncItems는 UnsupportedOperationException을 던지므로 기능 테스트 실패가 정상입니다.
빈 테스트나 @Disabled는 없습니다. 테스트를 고쳐 통과시키지 말고 구현을 완성하세요.

앱은 8080 포트에서 실행됩니다. H2 콘솔: http://localhost:8080/h2-console
JDBC URL: `jdbc:h2:mem:cart-sync`, 사용자 `sa`, 비밀번호 없음.
메모리 DB이므로 재시작하면 초기화됩니다. 운영 설정이 아닌 로컬 실습용입니다.
앱에는 자동 seed가 없습니다. 아래 SQL을 H2 콘솔에서 실행해 수동 요청을 실습할 수 있습니다.

```sql
insert into products(name, price) values ('키보드', 12000.50), ('마우스', 3500.25), ('케이블', 999.99);
insert into carts default values;
-- 새 앱 실행 후의 ID 기준. 이미 데이터가 있다면 SELECT로 실제 ID를 확인하세요.
insert into cart_items(cart_id, product_id, quantity) values (1, 1, 2), (1, 2, 3);
```

## API 및 데이터 조건

`PUT /api/carts/{cartId}/items`

```json
{"items":[{"productId":1,"quantity":4},{"productId":3,"quantity":2}]}
```

- Cart → CartItem → Product. 장바구니마다 동일 상품은 최대 한 행입니다.
- Product.name, Product.price는 필수이며 실습 데이터의 가격은 0 이상의 소수 둘째 자리 값입니다.
- unitPrice는 동기화 시점 Product.price, linePrice는 unitPrice × quantity입니다.
- 응답은 cartId, totalQuantity, totalPrice, items 배열을 포함합니다.
- 각 응답 item은 productId, productName, quantity, unitPrice, linePrice를 포함합니다.
- 응답 배열의 정렬 순서는 자유입니다. 금액은 BigDecimal로 정확하게 계산하세요.
- 가격 변경 이력, 재고 차감, 사용자 인증, 동시 요청 충돌 해결은 이번 범위 밖입니다.

## 기능/비기능 요구사항 및 제약

1. 기존 상품은 수량만 UPDATE하고 기존 CartItem.id를 유지합니다.
2. 신규 상품은 INSERT, 요청에 빠진 기존 상품은 DELETE합니다.
3. 수량이 동일한 상품은 불필요한 UPDATE/INSERT/DELETE를 하지 않습니다.
4. `{"items":[]}`는 전체 비우기이며 실제 DB 행도 삭제되어야 합니다.
5. 중복 productId, quantity ≤ 0, null 항목/필드/목록은 잘못된 요청입니다.
6. 존재하지 않는 상품 또는 장바구니 요청은 실패합니다.
7. 실패 시 이전 장바구니 상태와 행 ID가 그대로 보존되어야 합니다. 다른 장바구니를 변경하지 마세요.
8. 전체 DELETE 후 전체 INSERT는 금지합니다. 특정 정답 기술이나 쿼리 개수 상한은 강제하지 않습니다.
9. 동일한 최종 상태를 반복 전송했을 때 추가 쓰기가 없어야 합니다.

### 오류 계약

핵심 서비스의 실패는 제공된 `MissionException`으로 표현하세요.
중복/잘못된 수량/구조는 `INVALID_REQUEST`, 없는 상품은 `PRODUCT_NOT_FOUND` (HTTP 400),
없는 장바구니는 `CART_NOT_FOUND` (HTTP 404)입니다. 메시지 문구는 자유입니다.
Controller의 Bean Validation과 별개로 서비스 직접 호출의 잘못된 수량도 검증합니다.
모든 예외를 잡아 성공 응답으로 바꾸거나 UnsupportedOperationException을 그대로 두면 테스트를 통과할 수 없습니다.

## 구현 TODO

- `CartService.syncItems(Long cartId, SyncCartItemsRequest request)` 구현.
- 트랜잭션 경계, 검증과 변경의 순서, 응답 생성 방식을 직접 결정.
- 필요시 Repository 메서드, 양방향 연관관계 편의 메서드 추가.
- 현재 매핑은 컴파일과 조회를 위한 최소 매핑입니다. cascade/orphanRemoval/fetch는 정답 설정이 아닙니다.
  필요하다고 판단하면 직접 조정하세요. 서비스와 매핑을 완성하면 제공 테스트를 그대로 실행할 수 있습니다.

## 테스트 시나리오와 완료 기준

`CartSyncIntegrationTest`는 매 테스트마다 실제 H2에 데이터를 커밋합니다.
테스트 자체에는 @Transactional이 없고 서비스 실행 후 JDBC 및 새 트랜잭션으로 재조회합니다.
테스트 프레임워크가 서비스의 트랜잭션 누락을 대신 감춰주지 않습니다.

| 시나리오 | 검증 |
|---|---|
| 픽스처 점검 | 커밋된 행 및 연관관계 재조회 |
| 기존 수량 변경 | 수량 변경, 기존 행 ID 보존 |
| 추가+수정+삭제 | 최종 DB 상태, 유지 항목 ID, 삭제 행 부재 |
| 전체 비우기 | DB 행 0개, 빈 items, 합계 0 |
| 중복 상품 | INVALID_REQUEST, 이전 행/수량/ID 보존 |
| 없는 상품 | 유효한 변경 요청 뒤 실패해도 이전 상태 보존 |
| 수량 0 및 -1 | 서비스 실패, 이전 상태 보존 |
| 합계 및 상세 응답 | 소수 금액, 이름/가격/수량/행 합계 정확성 |
| 없는 장바구니 | CART_NOT_FOUND, 기존 장바구니 보존 |
| 동일 상태 재전송 | CartItem 쓰기 SQL 없음, 행 ID 보존 |
| 상품 30개 | 최종 상태와 합계, SELECT 수 관찰 출력 |
| HTTP 검증 | 잘못된 수량 400, 정상 PUT 응답 계약 |

전체 테스트가 통과하고 SQL에서 필요한 변경만 발생하는지 확인하면 완료입니다.
자동 테스트가 모든 SQL 전략이나 동시성 문제까지 증명하지는 않습니다.
30개 테스트는 SELECT 수를 콘솔에 출력하며 임의의 상한으로 실패시키지 않습니다.
결과는 `build/reports/tests/test/index.html`에서 확인할 수 있습니다.

## 생각해볼 함정

- `cart.getItems().remove(...)`만 호출하면 DB DELETE가 발생할까요? 어떤 매핑과 영속 상태에 달려 있나요?
- `repository.delete(...)`만 호출했을 때 메모리의 cart.items와 응답에는 무엇이 남나요?
- 양방향 연관관계의 두 참조는 언제, 누가 맞춰야 하나요?
- 유효한 항목을 먼저 바꾼 뒤 마지막 상품 검증에 실패하면 무엇이 커밋되나요?
- 같은 상품 30개가 아니라 서로 다른 상품 30개일 때 Product SELECT가 N회 발생하나요?
- 1개 요청과 30개 요청의 SQL을 비교해보세요. 어떤 조회가 증가하며 어떤 개선 선택지가 있나요?
- 합계 계산 전에 객체 그래프와 DB의 상태가 달라지면 어떤 응답이 나올까요?
- 동일 수량 전송에서도 쓰기가 발생한다면 원인은 무엇인가요?

선택한 방식, 관찰한 SQL 수, 대안과 trade-off를 직접 기록하세요.

## 스타터 생성 시 검증 기록 (2026-09-11)

- Java 21.0.12, Gradle wrapper 8.14.3으로 `testClasses bootJar` 성공.
- 실행 JAR를 임시 HTTP 포트에서 기동하여 Tomcat 및 애플리케이션 시작 확인 후 검증 프로세스 종료.
- `test`: 총 23개, 7개 통과 / 16개 실패 / 비활성화 0개.
- 통과: DB 픽스처 재조회, HTTP 잘못된 수량, HTTP null 구조 5개.
- 서비스 null 목록/항목/productId/quantity 거부 테스트 4개도 포함합니다.
- 실패 16개 전부 `TODO: implement CartService.syncItems`의 UnsupportedOperationException에서 유래함을 XML로 확인.
- 소스·테스트 컴파일 오류, Spring 설정 오류, 픽스처 오류는 발견되지 않았습니다.
- 핵심 구현 후 성공 경로 assertion 전체가 통과하는지는 학습자가 다시 실행해 확인해야 합니다.
