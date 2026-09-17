# Starter 검증 기록 — 2026-09-17

환경: Windows, Temurin JDK 21.0.12.1, Gradle wrapper 8.14.3, Spring Boot 3.5.16.

## 검증 방법

1. 최종 서비스와 동일한 TODO 상태에서 `gradlew.bat testClasses --no-daemon --console=plain` 실행: main/test 컴파일 성공.
2. TODO 상태에서 전체 `test` 실행: 18개 중 기반 검증 9개 통과, acceptance 9개 실패.
3. 검증용 임시 서비스 구현을 이용해 acceptance assertion의 끝까지 실제 실행: 전체 18개 통과. 정상/실패/중복/재처리/실제 SQL 장애 경로 모두 실행됨.
4. `finally`에서 서비스 원본 바이트를 복원. 임시 구현은 제공 프로젝트에 남기지 않음. 임시 구현은 테스트 코드의 실행 가능성을 확인하기 위한 것으로 운영 안전성이나 모든 장애 복구 가능성의 증명이 아님.
5. 실제 study 저장소의 최종 디렉터리에서 다시 `testClasses`, 전체 `test` 실행: 컴파일 성공, 18개 중 9개 통과 / TODO에 의한 9개 실패 / skipped 0개. XML 리포트의 모든 실패에 TODO 예외가 포함됨을 확인.

## 최종 실제 결과와 원인 분류

| 테스트 클래스 | 개수 | 결과/원인 |
|---|---:|---|
| OrderControllerTest | 5 | 통과: HTTP routing, JSON 필드, 404/409/503, 양수 ID validation |
| StarterInfrastructureTest | 4 | 통과: Spring context, committed fixture, fake 기록/실패/reset, H2 trigger/flush rollback/직접 SQL |
| CancelOrderAcceptanceTest | 9 | 핵심 TODO 미구현에 의한 의도적 실패 |

acceptance 실패 중 7개는 TODO `UnsupportedOperationException`이 직접 전파됩니다. 나머지 2개(없는 주문/취소 불가 상태)는 요구된 비즈니스 예외 대신 TODO 예외가 나와 assertion이 실패합니다. `processRefund` 단독 fixture 테스트는 해당 메서드의 TODO에 도달합니다. compile/context/fixture/expected value 오류에 의한 실패는 발견되지 않았습니다.

컴파일 성공과 전체 테스트 성공은 다릅니다. 최종 전체 테스트는 **의도적으로 실패**하며 핵심 구현 후 통과시켜야 합니다. Disabled/빈 테스트는 없습니다. Mockito 서비스는 HTTP slice 테스트에만 사용하며 acceptance는 실제 TODO 서비스를 호출합니다.

## 확인 범위

- JPA 저장/조회, 외부 fake 호출 파라미터와 횟수, SQL 레벨 실패 주입은 실제 실행으로 검증했습니다.
- 테스트 프레임워크는 순차 실행입니다. 동시성·실제 서버 kill·외부 결제사의 처리 특성·운영 DB 차이는 검증하지 않았습니다.
- Windows `gradlew.bat`으로 실행했습니다. Unix wrapper 스크립트와 JAR를 포함하지만 Unix OS에서의 실제 실행은 검증하지 않았습니다. Unix로 옮길 때 실행 권한은 README의 절차를 참고하세요.
- Gradle 9 호환성 관련 deprecation 안내 및 Mockito/JVM 클래스 공유 경고가 출력됩니다. 현재 고정한 Gradle 8.14.3에서 컴파일/테스트 실행을 막지 않았습니다.
