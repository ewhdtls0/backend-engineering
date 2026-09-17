# Backend Study

백엔드 개발자로서 필요한 문제 해결 능력을 실제 구현 과제를 통해 반복해서 연습하고 기록하는 저장소입니다.

단순히 예제 코드를 따라 작성하는 데서 끝내지 않고, 요구사항을 분석하고 직접 설계한 뒤 테스트와 실행 결과로 구현을 검증하는 것을 목표로 합니다. 각 과제에서 작성한 코드, 고민한 선택지, SQL 및 성능 관찰 결과, 코드리뷰와 회고를 함께 남깁니다.

## 학습 목표

- Java와 Spring Boot를 사용해 요구사항을 동작하는 코드로 구현하기
- JPA 연관관계, 트랜잭션, 영속성 컨텍스트의 동작을 이해하기
- 테스트를 통해 정상 동작과 예외·경계 조건을 검증하기
- N+1, 페이지네이션, 동시성 등 실무에서 발생하는 문제를 직접 해결하기
- 선택한 구현 방식의 장점과 한계를 설명할 수 있도록 기록하기
- 코드리뷰를 반영하며 읽기 쉽고 유지보수하기 좋은 코드로 개선하기

## 학습 진행 방식

각 과제는 다음 순서로 진행합니다.

1. README에서 서비스 상황과 요구사항을 확인합니다.
2. 제공된 테스트를 실행해 현재 실패 지점을 확인합니다.
3. 핵심 서비스와 Repository를 직접 구현합니다.
4. 테스트를 통과시킨 뒤 SQL과 실행 결과를 관찰합니다.
5. 구현 방식의 근거와 trade-off를 정리합니다.
6. 코드리뷰를 받고 개선점과 회고를 문서로 남깁니다.

테스트를 임의로 수정해서 통과시키기보다, 테스트가 표현하는 요구사항에 맞게 실제 구현을 수정합니다. 테스트만 통과하는 것을 최종 목표로 삼지 않고, 데이터가 증가하거나 운영 환경이 달라졌을 때의 문제도 함께 생각합니다.

## 저장소 구조

```text
study/
├── README.md
├── .gitignore
├── 2026-09-09-order-query/
│   ├── README.md
│   ├── CODE_REVIEW.md
│   ├── build.gradle
│   └── src/
├── 2026-09-10-price-history/
│   ├── README.md
│   ├── build.gradle
│   └── src/
├── 2026-09-11-cart-sync/
├── 2026-09-14-notification-job/
├── 2026-09-15-payment-request/
└── 2026-09-16-seat-hold/
```

각 날짜 디렉터리는 IntelliJ에서 별도로 열고 실행할 수 있는 독립 Gradle Spring Boot 프로젝트입니다. 루트 `study/` 디렉터리 전체는 하나의 Git 저장소로 관리합니다.

## 과제 목록

| Date | Mission | Topics | Review |
|---|---|---|---|
| 2026-09-09 | [주문 내역 조회 API](2026-09-09-order-query/README.md) | Spring, JPA, Pagination, Query Performance | [Code Review](2026-09-09-order-query/CODE_REVIEW.md) |
| 2026-09-10 | [상품 가격 변경 이력 조회](2026-09-10-price-history/README.md) | Spring, JPA, Transaction, Dirty Checking | - |
| 2026-09-11 | [장바구니 항목 동기화](2026-09-11-cart-sync/README.md) | Spring, JPA, Aggregate, Collection Sync | [Code Review](2026-09-11-cart-sync/CODE_REVIEW.md) |
| 2026-09-14 | [대량 알림 발송 작업](2026-09-14-notification-job/README.md) | Spring, JPA, Failure Isolation, Job Status | - |
| 2026-09-15 | [멱등 결제 승인](2026-09-15-payment-request/README.md) | Spring, JPA, Idempotency, Concurrency | - |
| 2026-09-16 | [만료되는 좌석 선점](2026-09-16-seat-hold/README.md) | Spring, JPA, Time, Concurrency, Atomicity | - |

새 과제는 `YYYY-MM-DD-mission-name/` 형식으로 추가하고 위 표에 학습 주제와 결과를 기록합니다.

## 기록 원칙

과제를 마친 뒤에는 다음 내용을 설명할 수 있는지 확인합니다.

- 어떤 요구사항을 구현했는가?
- 가장 어려웠던 문제는 무엇이었는가?
- 선택한 구현 방식은 무엇이고, 왜 선택했는가?
- 고려했던 다른 방법에는 무엇이 있는가?
- 테스트는 어떤 오류와 회귀를 막아주는가?
- SQL 수와 데이터 접근 방식은 데이터 증가에도 적절한가?
- 실서비스에 적용하려면 무엇을 추가하거나 변경해야 하는가?

이 저장소의 목적은 완성된 정답을 모으는 것이 아니라, 문제를 발견하고 검증하며 개선해 나가는 과정을 누적하는 것입니다.
