# 코드 리뷰 — 회원별 최근 활동 피드 조회

- 리뷰일: 2026-09-18
- 대상: `2026-09-18-activity-feed` 현재 작업 트리
- 검증 환경: Windows, Java 21.0.12, Spring Boot 3.5.16, Gradle 8.14.3
- 검증 명령: `gradlew.bat test --rerun-tasks --no-daemon --console=plain`
- 검증 결과: 수정 전 33개 테스트 통과, 리뷰 반영 후 전체 34개 테스트 통과

## 종합 평가

두 테이블을 `UNION ALL`로 합친 뒤 DB에서 하나의 정렬과 offset/limit를 적용한 선택은 이번 과제의 정확성 요구를 잘 충족한다. 한쪽 활동이 훨씬 많거나 특정 시간대의 최신 활동이 한 테이블에 몰려 있어도, 합쳐진 전체 집합에서 페이지를 자르므로 각 테이블에서 `size`개씩 가져와 애플리케이션에서 합치는 방식의 오류를 피했다.

`(created_at DESC, type DESC, id DESC)`도 완전한 정렬 순서다. type이 다르면 POST와 COMMENT가 구분되고, type이 같으면 해당 테이블의 PK인 id가 유일하므로 동일한 createdAt에도 순서가 결정된다. 인터페이스 projection을 사용해 활동 엔티티를 적재하지 않고 응답 크기만큼의 결과만 애플리케이션으로 받는 점도 좋다.

현재 구현은 기능 테스트와 5만 건 데이터 테스트를 모두 통과했다. 아래 리뷰 항목은 이후 수정에서 모두 반영됐다.

**최종 판정: 리뷰 반영 완료**

## 수정 완료 기록

- `Page` 반환과 전체 count query를 제거하고 필요한 projection 행만 조회
- offset을 long으로 계산하고 native SQL에 직접 전달하여 큰 page의 조기 빈 응답 제거
- posts/comments에 `(member_id, created_at DESC, id DESC)` 복합 인덱스 추가
- H2 실행 계획에서 각 branch의 복합 인덱스 사용 검증
- 동일 createdAt의 `POST 우선, type별 id 내림차순` 규칙을 테스트로 명시
- README 구현 기록과 완료 기준, VERIFICATION을 현재 상태로 갱신
- 최종 34개 테스트 통과

아래 내용은 수정 전 구현을 기준으로 남긴 리뷰 기록이다.

## 잘 구현한 부분

### 1. 합친 전체 집합에서 페이지를 계산한다

대상: `MemberRepository.java:13-57`

```sql
select ...
from (
    select ... from posts where member_id = :memberId
    union all
    select ... from comments where member_id = :memberId
) activity
order by activity.created_at desc, activity.type desc, activity.id desc
```

두 종류의 활동을 먼저 하나의 논리적 집합으로 만든 뒤 정렬과 pagination을 적용한다. 따라서 다음처럼 데이터가 치우친 경우에도 정확하다.

```text
최신 25개가 모두 POST
그 다음 20개가 모두 COMMENT

page 0 = POST 20개
page 1 = POST 5개 + COMMENT 15개
page 2 = COMMENT 5개
```

각 테이블에서 매번 `size`개만 읽는 구현이라면 page 1 이후의 전역 offset을 정확히 적용하기 어렵지만, 현재 쿼리는 DB가 합친 결과에 offset을 한 번 적용하므로 이 문제가 없다.

### 2. 동일 시각에도 완전한 순서를 만든다

대상: `MemberRepository.java:39`

```sql
order by activity.created_at desc,
         activity.type desc,
         activity.id desc
```

정렬 의미는 다음과 같다.

1. 작성 시각이 최신인 활동 우선
2. 작성 시각이 같으면 문자열 내림차순 기준으로 POST 우선
3. 작성 시각과 type이 같으면 id가 큰 활동 우선

POST와 COMMENT의 id가 같아도 type이 먼저 비교되므로 충돌하지 않는다. 같은 type 안에서는 PK id가 유일하므로 마지막 동률도 남지 않는다.

### 3. 활동 엔티티를 대량 적재하지 않는다

대상: `ActivityProjection.java:7-11`, `ActivityServiceImpl.java:42-49`

native query 결과를 projection으로 받고 바로 `ActivityResponse`로 변환한다. Post와 Comment 엔티티를 영속성 컨텍스트에 올리거나 두 테이블의 전체 결과를 Java 컬렉션에 담아 정렬하지 않는다.

대량 테스트에서 page 0과 page 37 모두 다음 값이 관찰됐다.

```text
JDBC result rows = 22
Hibernate entity loads = 1
```

22행은 Member 조회 1행, 활동 응답 20행, count 결과 1행이다. 활동 50,000개 전체가 애플리케이션으로 전달되지 않는다는 근거가 된다.

### 4. 회원 부재를 활동 조회 전에 처리한다

대상: `ActivityServiceImpl.java:28-29`

Member를 먼저 조회하고 없으면 `MemberNotFoundException`을 던진다. 테스트도 이 경우 posts/comments SQL이 실행되지 않고 JDBC 결과 행이 최대 1개인지 확인한다. HTTP 404 매핑과 Controller 입력 검증도 별도 테스트로 검증되어 있다.

### 5. 읽기 트랜잭션과 불변 응답 경계가 명확하다

대상: `ActivityServiceImpl.java:22`, `ActivityPageResponse.java:7-8`

서비스에 `@Transactional(readOnly = true)`가 있어 OSIV가 꺼진 환경에서도 조회 경계가 명확하다. 현재 projection은 LAZY 연관관계에 접근하지 않으므로 불필요한 추가 조회도 없다. 응답 record가 `List.copyOf()`로 목록을 복사하여 서비스 밖에서 결과가 변경되는 것도 막는다.

## Important — 우선 보완 권장

### 1. offset이 Integer.MAX_VALUE를 넘으면 실제 데이터 여부와 관계없이 빈 페이지를 반환한다

대상: `ActivityServiceImpl.java:31-40`

```java
if (pageRequest.getOffset() > Integer.MAX_VALUE) {
    return new ActivityPageResponse(memberId, page, size, List.of());
}
```

이 방어 코드는 `page * size`의 int overflow를 피하지만, API가 허용한 유효한 page를 정확히 조회한다는 계약과는 다르다. 예를 들어 `page=21_474_837`, `size=100`일 때 offset은 2,147,483,700이다. 해당 위치에 실제 활동이 있어도 현재 구현은 무조건 빈 목록을 반환한다.

현재 큰 page 테스트는 활동이 1개뿐이라 overflow로 앞 페이지 데이터가 다시 나오는지만 확인하고, 실제로 큰 offset 위치에 데이터가 있는 상황은 확인하지 못한다.

운영 도메인에서 회원별 활동 수가 int 범위를 넘을 수 없다는 상한을 명시하고 API validation으로 제한하거나, 사용하는 DB/driver가 지원하는 long offset을 직접 전달하는 조회 방식을 선택하는 편이 정확하다. 단순히 빈 페이지로 간주하려면 그 판단의 전제도 문서화해야 한다.

### 2. 응답에 전체 개수가 없는데 매 요청마다 전체 count 쿼리를 실행한다

대상: `MemberRepository.java:41-57`, `ActivityServiceImpl.java:42`

Repository 반환형이 `Page<ActivityProjection>`이므로 Spring Data는 content 쿼리 외에 `countQuery`도 실행한다. 하지만 서비스는 Page의 totalElements나 totalPages를 사용하지 않고 content만 stream으로 변환한다.

실제 대량 테스트 로그에서도 요청마다 다음 세 쿼리가 실행됐다.

```text
1. Member 존재 확인
2. UNION ALL + 정렬 + offset/limit
3. posts/comments 전체 개수를 합산하는 count
```

회원 활동이 커질수록 count는 매 요청마다 두 테이블의 해당 회원 키를 모두 세는 비용이 든다. 전체 개수가 응답 계약에 필요하지 않으므로 `List<ActivityProjection>` 또는 `Slice<ActivityProjection>` 기반 계약을 검토하는 것이 좋다. Slice를 쓰면 `size + 1`개로 다음 페이지 존재 여부를 알 수 있고, List라면 현재 응답에 필요한 행만 받을 수 있다.

### 3. 조회 조건과 정렬을 지원하는 복합 인덱스가 없다

대상: `Post.java:7`, `Comment.java:7`, `MemberRepository.java:20-39`

현재 두 테이블은 명시적인 복합 인덱스가 없다. H2가 FK인 `member_id` 인덱스를 자동 생성할 수는 있지만, 그 인덱스만으로는 회원별 `created_at DESC, id DESC` 순서를 제공하지 못한다.

각 테이블에 다음 인덱스를 우선 검토할 수 있다.

```text
posts    (member_id, created_at DESC, id DESC)
comments (member_id, created_at DESC, id DESC)
```

`type`은 각 UNION branch 안에서 상수이므로 테이블 인덱스에 넣을 컬럼이 아니다. 이 인덱스는 회원 조건으로 범위를 좁힌 뒤 각 테이블에서 필요한 정렬 순서로 읽는 데 도움을 준다. 다만 최종적으로 두 branch를 합친 외부 정렬과 큰 offset의 건너뛰기 비용까지 제거한다고 단정하면 안 된다. 실제 사용하는 DB에서 `EXPLAIN`으로 range scan, sort, 반환 전 처리 행 수를 확인해야 한다.

### 4. 과제 README와 검증 기록이 구현 전 상태로 남아 있다

대상: `README.md:77-84`, `91`, `129`, `157-180`, `VERIFICATION.md:7-27`

README는 아직 서비스를 TODO라고 설명하고 완료 체크박스와 구현 기록을 비워 두었다. VERIFICATION도 23개 통과·10개 의도된 실패라고 적혀 있다. 현재 실제 상태는 핵심 서비스가 구현되었고 33개 테스트가 모두 통과한다.

README 구현 기록에는 최소한 다음을 작성하는 편이 좋다.

- 동일 시각 규칙: `createdAt DESC, type DESC, id DESC`
- `UNION ALL` 결과에 전역 offset/limit를 적용하므로 페이지가 정확한 이유
- page 0/37에서 JDBC 22행, entity 1개가 관찰된 사실과 count 1행의 의미
- 큰 page에서 DB가 offset만큼 읽고 버릴 수 있는 비용
- page 요청 사이 insert/delete가 만드는 중복·누락
- 두 테이블에 필요한 복합 인덱스와 실제 실행 계획

VERIFICATION도 현재 실행 결과인 33/33으로 갱신해야 문서가 코드와 맞는다.

## Minor — 테스트와 구조 개선

### 5. 동일 시각 테스트가 구현의 실제 tie-breaker를 직접 확인하지 않는다

대상: `ActivityServiceTest.java:67-82`

현재 테스트는 size 100으로 받은 첫 결과를 baseline으로 삼고, 반복 조회와 size 7 페이지 연결 결과가 baseline과 같은지 확인한다. 결정성과 페이지 경계 중복·누락을 확인하는 좋은 테스트지만, `POST 우선, id 내림차순`이라는 실제 규칙 자체는 검증하지 않는다.

구현 규칙을 확정했다면 같은 createdAt에 다음 데이터를 만들고 명시적으로 순서를 확인하는 테스트를 추가할 수 있다.

```text
POST id 큰 값 → POST id 작은 값 → COMMENT id 큰 값 → COMMENT id 작은 값
```

이렇게 하면 ORDER BY의 type 또는 id 조건이 실수로 빠지는 회귀를 더 직접적으로 발견한다.

### 6. 대량 테스트는 애플리케이션으로 반환된 행 수를 잘 보지만 DB 내부 비용은 보지 못한다

대상: `ActivityVolumeTest.java:22-63`

`JdbcReadProbe`와 Hibernate statistics는 전량 entity/DTO 적재 회귀를 잡는 데 효과적이다. 반면 DB가 내부적으로 50,000행을 scan·sort한 뒤 20행만 반환해도 관찰 결과는 20행이다. 현재 page 0과 37의 기대 결과도 모두 POST가 되도록 구성되어 있어 대량 데이터에서 두 종류가 촘촘히 섞이는 경계는 확인하지 않는다.

보완한다면 다음을 분리해 관찰하는 것이 좋다.

- 현재 테스트: 애플리케이션 결과 행과 entity 적재 상한
- 실행 계획 테스트/수동 기록: 인덱스 사용, sort 여부, scan rows
- 추가 fixture: 동일 시각 또는 촘촘히 교차하는 POST/COMMENT가 페이지 경계를 통과하는 경우
- 더 깊은 page 비교: offset 증가에 따른 실행 시간과 처리 행 수

### 7. 조회 책임의 위치를 더 분명하게 표현할 수 있다

대상: `MemberRepository.java:13-57`

동작에는 문제가 없지만 Post와 Comment의 통합 조회가 `MemberRepository`에 들어 있어 이름만 보면 책임을 예상하기 어렵다. 현재 규모에서는 충분히 단순하지만 쿼리가 확장되면 `ActivityQueryRepository` 같은 읽기 전용 컴포넌트로 분리하면 의도가 더 선명해진다. 이는 필수 수정 사항은 아니다.

## 요청 사이 데이터 변경과 offset pagination

현재 API는 요청마다 독립된 read-only transaction을 사용한다. page 0과 page 1이 같은 snapshot을 공유하지 않는다.

예를 들어 처음 데이터가 다음과 같고 size=3이라고 하자.

```text
초기: A B C | D E F
page 0 응답: A B C
```

그 뒤 더 최신인 X가 추가되면 정렬 결과는 다음처럼 바뀐다.

```text
변경 후: X A B | C D E | F
page 1 응답: C D E
```

C는 page 0에서도 이미 받았으므로 중복된다. 반대로 page 0 뒤에 앞쪽 데이터가 삭제되면 기존 page 1의 첫 항목이 page 0 쪽으로 당겨져 누락될 수 있다.

현재 page/size 계약을 유지하는 한 이런 현상은 offset pagination의 성질이다. 제품에서 허용 가능한지 먼저 결정해야 한다. 더 강한 일관성이 필요하면 첫 요청의 기준 시각/정렬 키를 후속 요청에 전달하거나 cursor 방식으로 API 계약을 확장하는 선택을 검토할 수 있다.

## 권장 보완 순서

1. README와 VERIFICATION을 현재 구현 및 33개 테스트 결과로 갱신한다.
2. `Page`가 만드는 불필요한 count 쿼리를 제거한다.
3. 두 테이블에 `(member_id, created_at DESC, id DESC)` 인덱스를 추가하고 실제 DB 실행 계획을 기록한다.
4. 큰 offset을 무조건 빈 결과로 처리하는 정책을 정확한 계약 또는 명시적인 상한으로 바꾼다.
5. 동일 시각 정렬 규칙을 직접 검증하는 테스트를 추가한다.
6. 인덱스 적용 전후와 page 증가에 따른 scan/sort/offset 비용을 비교한다.

## 면접·회고 질문

- 두 테이블에서 각각 `size`개만 가져오는 방식이 page 0에서는 맞아 보여도 page 1 이후 틀릴 수 있는 반례는 무엇인가?
- `(createdAt, type, id)`가 완전한 정렬 키인 이유는 무엇인가? type과 id 중 하나를 빼면 어떤 동률이 남는가?
- `UNION` 대신 `UNION ALL`을 쓴 이유와 중복 제거 비용의 차이는 무엇인가?
- JDBC 결과 행이 22개라는 사실은 무엇을 보장하고, DB 내부 scan 행에 대해서는 무엇을 보장하지 못하는가?
- 응답에서 전체 개수를 사용하지 않는데 `Page`를 반환하면 어떤 추가 쿼리가 생기는가?
- 복합 인덱스에서 `member_id`가 첫 컬럼이어야 하는 이유는 무엇인가?
- offset이 커질수록 인덱스가 있어도 비용이 증가하는 이유는 무엇인가?
- page 0과 page 1 사이에 새 활동이 추가될 때 중복이 생기는 과정을 설명할 수 있는가?
- cursor pagination으로 바꾼다면 동일 시각까지 포함해 어떤 값을 cursor에 담아야 하는가?

## 최종 판단

핵심 난점인 서로 다른 두 테이블의 정확한 통합 페이지를 DB 한 번의 논리적 정렬로 해결했고, 전체 활동 엔티티를 애플리케이션에 올리지 않았다. 동일 시각의 순서도 완전하며 회원 부재·입력 오류·한 종류만 있는 회원·치우친 대량 데이터까지 테스트가 잘 받쳐 준다.

다음 단계의 핵심은 결과 정확성보다 DB가 그 20행을 만들기 위해 수행하는 일이다. count 제거, 복합 인덱스, 실행 계획 관찰을 적용하면 현재 구현의 장점을 유지하면서 데이터 증가에 더 잘 견디는 조회로 다듬을 수 있다.
