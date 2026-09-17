# 구현 검증 기록 — 2026-09-17

환경: Windows, Temurin JDK 21.0.12.1, Gradle wrapper 8.14.3, Spring Boot 3.5.16.

## 검증 절차

1. 삭제됐던 안전성 테스트 3개를 상태 의미에 맞게 복원했습니다.
2. 기존 구현에서 테스트를 실행해 다음 세 결함이 실제로 실패하는지 확인했습니다.
   - `CANCELING / REFUND_PENDING`을 `COMPLETED`로 응답
   - `REFUND_PENDING`을 다시 외부 호출
   - 외부 성공 뒤 완료 저장 실패 시 준비 상태가 보존되지 않음
3. 준비·외부 호출·완료의 트랜잭션 경계를 분리했습니다.
4. 동시 취소 테스트를 추가해 첫 외부 호출이 진행 중일 때 두 번째 요청이 환불을 재호출하지 않는지 확인했습니다.
5. `testClasses`와 전체 `test`를 최종 프로젝트에서 새로 실행했습니다.

## 최종 결과

| 테스트 클래스 | 개수 | 결과 |
|---|---:|---|
| `CancelOrderAcceptanceTest` | 11 | 통과 |
| `OrderControllerTest` | 5 | 통과 |
| `StarterInfrastructureTest` | 4 | 통과 |
| 합계 | 20 | 통과, skipped 0 |

검증 범위는 H2/JPA 실제 commit, gateway 호출 횟수와 파라미터, 확정 거절 재처리, 결과 불명 재호출 차단, SQL failure injection, 동일 주문의 동시 요청을 포함합니다.

실제 프로세스 강제 종료와 운영 DB별 잠금 동작, 실제 결제사의 timeout 이후 결과 조회는 자동화 범위에 포함하지 않습니다. Gradle 9 호환성 deprecation 안내와 Mockito/JVM class sharing 경고는 현재 Gradle 8.14.3의 컴파일 및 테스트 결과에 영향을 주지 않았습니다.
