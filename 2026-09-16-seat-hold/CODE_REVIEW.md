# 코드 리뷰 — 만료되는 좌석 선점

리뷰 일자: 2026-09-17
대상: `2026-09-16-seat-hold`

## 검증 결과

- `gradlew.bat test` 성공
- hold / confirm 정상 흐름, 만료 경계, 과거 row 유지, 동시 hold, DB 쓰기 실패 rollback 테스트가 포함되어 있다.
- `Clock`을 주입해 시간 테스트가 실제 대기 없이 실행된다.
- Seat 행 비관적 락과 서비스 트랜잭션이 같은 좌석의 동시 hold를 직렬화한다.

테스트가 통과해도, 아래 사례는 현재 테스트가 직접 재현하지 않아 운영 환경에서 문제가 될 수 있다.

## P2 — 만료 순간에 confirm이 성공할 수 있음

위치: `src/main/java/study/seathold/service/SeatHoldService.java:86`

현재 코드는 현재 시각을 두 번 읽는다.

```java
if (seatHold.getExpiresAt().isEqual(LocalDateTime.now(clock))
        || seatHold.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
    // ...
}
```

첫 번째 호출이 만료 직전이고 두 번째 호출이 정확히 만료 시각이면, 두 비교가 모두 false가 될 수 있다. 그 결과 `now >= expiresAt`인데도 confirm이 성공한다.

현재 시각을 한 번만 읽고, `now >= expiresAt`을 한 번의 비교로 판단한다. 예를 들면 `!seatHold.getExpiresAt().isAfter(now)`이다.

현재의 고정 Clock 테스트는 호출마다 시간이 흐르지 않으므로 이 사례를 발견하지 못한다. 만료 직전과 만료 시각을 순서대로 반환하는 테스트 Clock으로 회귀 테스트를 추가할 수 있다.

## P2 — 이미 EXPIRED인 hold가 404로 응답됨

위치: `src/main/java/study/seathold/repository/SeatHoldRepository.java:20`

`findByIdWithSeat`는 `status = HELD`인 row만 조회한다. 따라서 DB에 존재하지만 이미 `EXPIRED`로 저장된 hold는 없다고 판단되어 404가 된다.

만료 hold를 confirm하면 409 Conflict가 되어야 하므로, hold는 ID 기준으로 먼저 조회하고 서비스에서 `HELD`, `EXPIRED`, `CONFIRMED` 상태를 구분하는 편이 명확하다. `EXPIRED` fixture를 직접 만든 뒤 confirm이 409인지 검증하는 테스트도 추가한다.

## P2 — 새 hold 차단 조회가 status를 고려하지 않음

위치: `src/main/java/study/seathold/repository/SeatHoldRepository.java:16`

현재 쿼리는 `expiresAt > now`만 본다.

```jpql
select sh from SeatHold sh
where sh.seat.id = :seatId and sh.expiresAt > :now
```

유효 선점은 `HELD && expiresAt > now`이다. 현재 형태에서는 실수로 미래 `expiresAt`을 가진 `EXPIRED` row가 생기거나, 상태 모델을 확장했을 때 새 hold가 잘못 차단된다.

조회 조건에 `status = HELD`를 넣고, 미래 만료 시각의 `EXPIRED` fixture가 새 hold를 막지 않는 테스트를 추가한다.

## P2 — DB 락 대기 시간 초과가 500으로 노출될 수 있음

위치: `src/main/java/study/seathold/exception/ApiExceptionHandler.java`

hold와 confirm은 비관적 락을 사용한다. 잠금을 보유한 트랜잭션이 길어져 H2 설정의 lock timeout을 넘기면 Spring이 lock-acquisition 예외를 던진다. 현재 advice는 `SeatHoldException`과 검증 예외만 처리하므로 이 경우 500 응답이 될 수 있다.

락 획득 실패 또는 락 timeout에 해당하는 Spring/JPA 예외를 409 Conflict 같은 업무 응답으로 변환한다. 한 트랜잭션이 좌석 락을 잡은 동안 다른 HTTP 요청을 실행하는 통합 테스트로 응답 계약도 검증한다.

## 좋았던 점

- hold에서 Seat 행을 먼저 잠근 뒤 유효 HELD를 확인하므로, 같은 좌석의 동시 hold에 필요한 임계 구역이 명확하다.
- confirm에서 SeatHold와 Seat 상태를 한 트랜잭션 안에서 변경하고, 테스트 전용 DB Trigger로 어느 테이블의 쓰기 실패도 rollback되는지 확인한다.
- `now < expiresAt`이라는 경계 규칙을 09:05와 09:06 테스트로 표현했다.
- DB 상태는 JDBC로 다시 읽어 JPA 1차 캐시가 테스트 결과를 가리는 일을 피했다.

## 다음 수정 순서

1. confirm의 현재 시각을 한 번만 읽도록 바꾸고 회귀 테스트를 추가한다.
2. ID 조회와 상태 검증을 분리해 EXPIRED hold의 409 응답을 보장한다.
3. 유효 hold 조회에 HELD 상태 조건을 추가한다.
4. 락 timeout 예외 응답과 통합 테스트를 추가한다.

