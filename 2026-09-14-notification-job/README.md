# 2026-09-14 (월) — 대량 알림 발송 작업

Java 21 / Spring Boot 3.5.16 / Gradle 8.14.3 / Web, JPA, Validation, H2.
목표 시간 30~60분. 핵심 학습 주제는 **한 대상의 실패를 다른 대상과 분리하고 결과를 저장하기**입니다.

## 상황

운영자가 사용자 여러 명에게 점검 공지를 보냅니다. 외부 알림 서버가 중간에 실패하더라도 나머지 사용자는 계속 처리하고,
운영자는 작업이 끝난 뒤 각 대상의 성공/실패와 사유를 확인해야 합니다.
HTTP 접수와 발송은 분리합니다. 생성 시점에는 접수 사실과 모든 대상을 DB에 저장하며 실제 발송은 하지 않습니다.

## 실행

이 폴더를 IntelliJ 독립 Gradle 프로젝트로 열고 Project SDK / Gradle JVM을 Java 21로 설정하세요.
Gradle 설치는 필요 없으며 첫 실행은 의존성을 다운로드합니다.

```powershell
.\gradlew.bat testClasses
.\gradlew.bat test
.\gradlew.bat bootRun
```

```sh
./gradlew testClasses
./gradlew test
./gradlew bootRun
```

Windows에서 옮겨 실행 권한이 없는 경우 `chmod +x gradlew` 후 실행하세요. `sh gradlew test`도 가능합니다.
기본 포트는 8080으로 다른 과제와 동시에 실행하려면 포트를 변경하세요.
H2 콘솔: `http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:mem:2026-09-14-notification-job`, 사용자 `sa`, 비밀번호 없음.
DB는 메모리 기반이며 재시작 시 초기화됩니다. SQL과 바인딩 값은 DEBUG/TRACE로 출력합니다.
기본 Sender는 로그만 남기는 데모이며 실제 사용자에게 알림을 보내지 않습니다.

## API

### 접수 — `POST /api/notification-jobs`

```json
{"memberIds":[11,22,33],"message":"서비스 점검 안내"}
```

성공 HTTP 201:

```json
{"jobId":1,"status":"PENDING"}
```

### 실행 — `POST /api/notification-jobs/{jobId}/execute`

본문 없이 호출, 실행이 끝나면 HTTP 204. 이번 과제는 동기식 실행 메서드 호출로 충분합니다.
Kafka, 스케줄러, 비동기 실행 인프라는 필수가 아닙니다.

### 조회 — `GET /api/notification-jobs/{jobId}`

```json
{
  "jobId":1,"status":"COMPLETED_WITH_FAILURES","totalCount":3,
  "successCount":2,"failureCount":1,
  "targets":[
    {"memberId":11,"status":"SUCCESS","failureReason":null},
    {"memberId":22,"status":"FAILED","failureReason":"외부 서버 오류"},
    {"memberId":33,"status":"SUCCESS","failureReason":null}
  ]
}
```

## 도메인 조건

| 도메인 | 기본 필드 |
|---|---|
| NotificationJob | id, status, message, totalCount, createdAt |
| NotificationTarget | id, jobId, memberId, status, failureReason |

현재 target은 jobId 값으로 연결되어 있습니다. 객체 연관관계, FK/유일 제약, 조회 방식은 필요하면 직접 설계하세요.
memberId는 양의 Long 값입니다. 회원 테이블 및 실회원 존재 조회는 범위 밖입니다.
message는 공백 불가, 최대 2,000자. 대상 목록은 비어 있거나 null일 수 없고 null/0/음수 memberId도 거부합니다.
중복 memberId는 자동 제거하지 않고 전체 요청을 거부합니다.

## 기능·비기능 요구사항

1. 접수 성공 시 Job 1건과 요청된 Target 전부를 `PENDING`으로 저장합니다. 하나라도 유효하지 않으면 새 행을 남기지 않습니다.
2. 실행 시 Job을 `PROCESSING`으로 전환합니다. 이번 기본 계약은 Target ID 순서(접수 순서)대로 순차 발송입니다.
3. 대상별 Sender를 한 번 호출하고 성공/실패 상태를 영속화합니다. 성공의 failureReason은 null, 실패는 비어 있지 않은 사유입니다.
4. Sender가 첫 대상 또는 중간 대상에서 예외를 던져도 뒤의 대상을 처리합니다.
5. 기존 다른 작업을 변경하지 않습니다. 조회는 커밋된 DB 결과로 집계합니다.
6. 정상적인 단일 실행 종료 시 pending 대상이 남지 않고 successCount + failureCount = totalCount입니다.

### 최종 상태 정책

| 결과 | Job 상태 |
|---|---|
| 모두 성공 | COMPLETED |
| 일부 성공 + 일부 실패 | COMPLETED_WITH_FAILURES |
| 모든 대상 실패 | FAILED |

`FAILED`는 이 기본 과제에서 모든 외부 발송이 실패한 결과입니다. DB 전체 장애처럼 집계 자체가 불가능한 상태는 확장 설계로 구분하세요.
동일 Job 재실행/동시 실행, 프로세스 중단 후 복구, 자동 재시도는 기본 테스트 범위 밖입니다.

### 오류 계약

서비스 직접 호출도 `MissionException`으로 표현합니다.
중복/빈 목록/blank message 등은 `INVALID_REQUEST` (400), 없는 jobId 조회/실행은 `NOT_FOUND` (404).
개별 Sender 실패는 execute 전체 예외로 전파하는 대신 Target 결과에 기록합니다.
예외 메시지 문구는 자유이며 Controller의 입력 검증과 서비스의 검증을 모두 고려하세요.

## 구현 TODO

- `NotificationJobService.createJob(CreateJobRequest request)`
- `NotificationJobService.execute(Long jobId)`
- 필요시 Repository 메서드와 별도 처리 클래스를 추가하세요.

조회 서비스, 엔티티 상태 변경 메서드, DTO, Controller, 오류 응답, 기본 Sender는 제공됩니다.
핵심 두 메서드는 의도적으로 `UnsupportedOperationException`을 던집니다. 정답 트랜잭션 경계는 설정하지 않았습니다.

## 테스트 시나리오

`NotificationJobTest`는 실제 H2와 Spring 서비스를 사용합니다. 테스트 자체에는 `@Transactional`이 없습니다.
실행용 Job/Target fixture는 생성 서비스 TODO를 호출하지 않고 명시적으로 저장·커밋합니다.
외부 경계만 Fake로 대체하며 실제 호출 memberId, message와 DB 결과를 함께 검증합니다.

| 시나리오 | 결과 검증 |
|---|---|
| fixture 조회 | Spring 기동, 커밋된 Job/Target, GET 결과 |
| 작업 생성 | Job/Target 수·내용·초기 상태·생성 시각, 발송 없음 |
| 모두 성공 | 전원 발송, 성공 상태/집계, COMPLETED |
| 중간 실패 | 마지막 대상도 발송 및 성공 저장 |
| 첫 대상 실패 | 남은 두 대상도 처리 |
| 모두 실패 | 세 실패사유, FAILED, 실패 수 3 |
| 중복 요청 | INVALID_REQUEST, 기존 Job/Target 스냅샷 동일, 부분 생성 없음 |
| 빈 목록/blank message | 서비스 검증, DB 기존 상태 유지 |
| 없는 Job | GET 404, execute NOT_FOUND, 외부 발송 없음 |
| HTTP | 입력 400, 접수 201, 실행 204, 결과 조회 |

## 고민 포인트 — 해결책은 직접 선택

- execute 전체에 하나의 트랜잭션을 적용하면 어떤 실패까지 함께 롤백될까요?
- try/catch로 예외를 잡았더라도 DB 트랜잭션이 rollback-only라면 다음 대상의 결과 저장은 가능한가요?
- 동일 객체 내부 메서드 호출(self-invocation)이 Spring proxy를 통과하나요? 메서드에 붙인 트랜잭션 설정이 실제 적용되는지 어떻게 확인할까요?
- 외부 발송 성공 후 DB 결과 저장이 실패하면 실제 발송과 DB 상태가 일치하나요? DB 롤백으로 외부 발송도 취소할 수 있나요?
- Target 결과 저장이 실패한 경우 다음 대상은 계속 처리할까요? Job 집계는 무엇을 진실로 삼아야 할까요?
- 긴 외부 호출 동안 DB 연결/락을 유지할 때 비용은 무엇인가요?

### 선택 심화: 개별 DB 실패 격리

기본 테스트는 Sender 실패를 검증하며 DB 장애 격리를 증명하지 않습니다. 저장 실패 후 계속 진행/전체 중단/복구 대기 중 정책을 먼저 정하고,
실제 DB flush 실패를 주입하는 추가 테스트를 작성하세요. 단순 Mock 예외와 실제 rollback-only 상황을 구분하세요.
처리 순서의 특정 대상에서 실패하도록 하고, 이후 대상의 커밋 여부·Job 상태·재시도 시 중복 발송을 확인하세요.
기본 스타터가 이 선택을 대신 구현하거나 트랜잭션 전파 방식을 강제하지 않습니다.

## 완료 기준

전체 테스트 통과, SQL로 결과 저장 확인, 일부 실패 후 나머지 처리가 지속됨을 설명할 수 있어야 합니다.
선택한 트랜잭션 경계와 외부 API/DB 사이의 불일치 위험, 처리 재개 정책을 기록하세요.
테스트 보고서는 `build/reports/tests/test/index.html`입니다. TODO 유래 실패는 초기 상태에서 정상이며 `@Disabled`로 숨기지 않습니다.
