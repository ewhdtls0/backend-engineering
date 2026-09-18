# 구현 검증 기록

검증일: 2026-09-18 · Java 21.0.12 · Spring Boot 3.5.16 · Gradle 8.14.3

## 최종 실행 결과

- Windows `gradlew.bat testClasses`: main/test 컴파일 성공.
- Windows `gradlew.bat test --rerun-tasks --no-daemon --console=plain`: 전체 34개 통과, 실패 0개, skip 0개.
- Unix용 `gradlew`는 스타터 단계에서 Windows Git Bash 실행을 확인했습니다. Linux/macOS 실제 OS에서 실행했다는 의미는 아닙니다.

| 테스트 클래스 | 실행 | 통과 | 실패 |
|---|---:|---:|---:|
| ActivityControllerTest | 16 | 16 | 0 |
| ActivityServiceTest | 8 | 8 | 0 |
| ActivityVolumeTest | 2 | 2 | 0 |
| BusinessBoundaryTest | 1 | 1 | 0 |
| StarterInfrastructureTest | 7 | 7 | 0 |

## 코드리뷰 반영 검증

- Spring Data `Page`와 count query를 제거하고 명시적인 `long offset`, `size`를 native query에 전달합니다.
- `page=Integer.MAX_VALUE`, `size=100`에서도 offset 계산이 overflow하지 않고 실제 통합 활동 SQL이 실행됩니다.
- 동일 createdAt에서는 `type DESC, id DESC` 규칙을 명시적으로 검증하고, size 7의 페이지를 다시 합쳐도 같은 순서인지 확인합니다.
- posts와 comments에 `(member_id, created_at DESC, id DESC)` 복합 인덱스가 생성됩니다.
- H2 `EXPLAIN` 결과가 각 테이블의 명시적 복합 인덱스를 참조하는지 검사합니다.
- 50,000개 활동의 page 0과 page 37에서 JDBC 결과는 각각 21행, Hibernate entity load는 1개입니다. SQL 목록에 count query가 없음을 함께 확인합니다.

## 검증 범위와 한계

- JDBC 21행은 Member 존재 확인 1행과 활동 projection 20행입니다. 애플리케이션이 50,000개 활동을 전부 받거나 엔티티로 적재하지 않는다는 근거입니다.
- 이 계측은 DB가 내부적으로 scan·sort·offset 처리한 행 수를 측정하지 않습니다. 실행 계획에서 branch 인덱스 사용은 확인하지만 최종 UNION 병합과 깊은 offset 비용은 남습니다.
- H2 실행 계획은 운영 DB의 optimizer 선택을 보장하지 않습니다. 실제 DB에서도 `EXPLAIN (ANALYZE)`와 깊은 page 지연 시간을 별도로 관찰해야 합니다.
- offset pagination은 서로 다른 요청 사이의 insert/delete로 인한 중복·누락을 막지 않습니다. 현재 page/size 계약의 한계로 README에 기록했습니다.
- 테스트 실행에서 JVM class-sharing 및 Gradle 9 관련 deprecation 안내가 나왔지만, 고정된 Gradle 8.14.3에서 컴파일·설정·fixture 오류는 없습니다.

## 저장소 변경 범위

오늘 미션의 서비스, 통합 조회, 엔티티 인덱스, 테스트, README, 코드리뷰와 이 검증 기록만 수정했습니다. 다른 날짜의 미션과 사용자 코드는 변경하지 않았습니다. 루트 README는 오늘 미션의 Code Review 링크만 보완했습니다. 커밋이나 push는 수행하지 않았습니다.
