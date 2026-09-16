# 대량 알림 발송 작업 코드리뷰

- 리뷰일: 2026-09-16
- 대상: `2026-09-14-notification-job`
- 검토 범위: 작업 접수, 대상별 발송, 결과 조회, 통합 테스트
- 테스트 실행: 현재 `JAVA_HOME`이 존재하지 않는 Java 17 경로를 가리켜 Gradle이 시작 전에 중단됨. Java 21 경로를 설정한 뒤 재실행이 필요함.

## 종합 평가

핵심 발송 흐름은 요구사항에 맞게 구현되었다.

- 접수 시 Job과 요청된 Target을 `PENDING`으로 저장하고 Sender를 호출하지 않는다.
- 실행 시 전달받은 Job의 Target만 Target ID 순서로 조회한다.
- 한 대상의 Sender 실패를 잡아 다음 대상 발송을 계속한다.
- 성공과 실패 결과를 Target에 저장하고, 성공 수에 따라 Job의 최종 상태를 결정한다.
- 다른 Job의 Target을 발송하거나 변경하지 않는지를 테스트로 확인한다.

다만 서비스 메서드를 Controller 밖에서 직접 호출했을 때의 입력 검증이 요구사항을 완전히 충족하지 못한다. 이 부분을 보완하면 과제의 오류 계약까지 일관되게 만족한다.

## 잘 구현한 부분

### 1. 접수와 발송의 분리

`createJob()`은 Job과 Target을 저장한 뒤 `PENDING` 응답만 반환한다. Sender 호출은 `execute()`에만 있으므로, 운영자가 작업을 접수하는 행위와 실제 발송이 분리된다.

```java
jobs.save(notificationJob);
targets.saveAll(notificationTargets);
return new CreateJobResponse(notificationJob.getId(), JobStatus.PENDING);
```

이 분리는 접수 후 실행 시점을 제어할 수 있게 하며, 접수 API가 외부 알림 서버의 응답 시간에 묶이지 않게 한다.

### 2. Job 범위를 제한한 대상 조회

```java
List<NotificationTarget> allTargets = targets.findByJobIdOrderByIdAsc(jobId);
```

전체 Target을 조회하지 않고 현재 Job의 Target만 가져온다. 다른 작업의 대상이 함께 발송되는 오류를 막고, 요구사항의 접수 순서 발송도 만족한다.

### 3. 대상별 실패 지속 처리

```java
try {
    sender.send(target.getMemberId(), notificationJob.getMessage());
    target.recordSuccess();
    successCount.incrementAndGet();
} catch (Exception e) {
    target.recordFailure("알림 전송 실패");
}
```

Sender 예외를 대상 단위에서 처리하므로 첫 번째나 중간 대상이 실패해도 남은 대상은 계속 처리된다. 성공·일부 실패·전체 실패를 각각 다른 Job 상태로 전환하는 흐름도 명확하다.

### 4. 실제 DB 상태를 확인하는 통합 테스트

테스트가 서비스 호출 직후 영속성 컨텍스트만 보지 않고 JDBC와 Repository를 함께 사용해 커밋된 결과를 검증한다. 특히 다른 Job의 `99L` Target을 만들고 실행 전후 DB 행을 비교해, 작업 범위가 섞이지 않는지를 확인한 점이 좋다.

## 보완할 부분

### 1. 서비스 직접 호출의 입력 검증 누락

대상: `NotificationJobService.createJob()`

현재 검증은 빈 목록과 blank 메시지, 중복 ID만 다룬다.

```java
if (request == null || request.memberIds().isEmpty() || StringUtils.isBlank(request.message())) {
    throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
}
```

아래 입력은 요구사항상 `MissionException(INVALID_REQUEST)`가 되어야 하지만 현재는 다른 결과가 나온다.

- `memberIds == null`: `isEmpty()`에서 `NullPointerException`
- 목록 안의 `null`: 저장 시 DB 제약 오류 가능
- `0` 또는 음수 ID: 정상 저장 가능
- 2,001자 초과 메시지: 저장 시 DB 제약 오류 가능

Controller의 Bean Validation은 HTTP 요청만 보호한다. 서비스는 테스트나 다른 서비스에서 직접 호출할 수 있으므로 같은 계약을 직접 지켜야 한다.

다음 순서로 검증하는 것을 권장한다.

```java
if (request == null
        || request.memberIds() == null
        || request.memberIds().isEmpty()
        || StringUtils.isBlank(request.message())
        || request.message().length() > 2_000
        || request.memberIds().stream().anyMatch(id -> id == null || id <= 0)) {
    throw new MissionException(MissionException.Code.INVALID_REQUEST, "잘못된 요청입니다.");
}
```

중복 검사는 이 검증 다음에 유지하면 된다. 저장 전에 모든 검증을 완료해야 잘못된 요청이 Job 또는 Target 일부만 남기는 일을 막을 수 있다.

### 2. 서비스 검증 테스트 보강

현재 `null`, `0` ID 같은 입력은 HTTP 검증 테스트에서만 다룬다. 따라서 Controller를 통하지 않는 호출에서 오류 계약이 깨져도 테스트가 통과할 수 있다.

다음 입력을 `service.createJob()`으로 직접 호출하는 테스트를 추가하는 것이 좋다.

- null 대상 목록
- null 대상 ID
- 0과 음수 대상 ID
- 2,001자 메시지

각 테스트는 `INVALID_REQUEST`뿐 아니라 Job과 Target 테이블이 실행 전과 완전히 같다는 것도 함께 확인해야 한다.

### 3. null Job ID의 오류 계약 정리

`execute(null)`과 `getJob(null)`은 `findById(null)`에 넘겨진다. Spring Data는 이 경우 `IllegalArgumentException`을 던질 수 있으므로, 서비스 오류 계약인 `MissionException`과 일관되지 않는다.

Job ID가 null 또는 양수가 아닐 때 `INVALID_REQUEST`를 던지도록 서비스 경계에서 처리하거나, API 계약에 허용하는 동작을 명시하는 편이 좋다.

## 테스트 가독성

`createsJobAndEveryTargetWithoutSending`은 이름 그대로 “접수는 Job과 모든 Target을 생성하지만 실제 발송은 하지 않는다”를 검증한다. Job 생성 수, Target의 ID·초기 상태, Sender 미호출을 모두 확인하므로 내용은 적절하다.

`99L`은 실행 대상이 아닌 별도 Job의 Target이다. `assertCompleted()`에서 Sender 호출 대상이 정확히 `33L`, `11L`, `22L`인지 검사하고, 다른 Job의 DB 행을 실행 전후로 비교하므로 `99L`이 발송되거나 변경되면 테스트가 실패한다. 의도를 더 분명히 하려면 `99L`을 `OTHER_JOB_MEMBER_ID` 상수로 이름 붙이고, 보조 메서드 위에 다른 Job 격리 검증이라는 설명을 추가할 수 있다.

## 권장 수정 우선순위

1. `createJob()`의 null·양수·메시지 길이 검증 추가
2. 해당 서비스 직접 호출 테스트와 원자성 검증 추가
3. null Job ID의 예외 정책 결정 및 구현
4. Java 21 경로 설정 후 전체 테스트 재실행

## 최종 판단

기본 기능 구현은 과제 요구사항을 충족한다. 특히 Job별 대상 범위 제한과 대상별 예외 격리는 올바르게 처리했다.

서비스 직접 호출 검증을 보완하고 Java 21 환경에서 전체 테스트를 다시 통과시키면, HTTP와 서비스 계층 모두에서 일관된 오류 계약을 가진 과제가 된다.
