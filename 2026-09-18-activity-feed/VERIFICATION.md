# 스타터 검증 기록

검증일: 2026-09-18 · Java 21.0.12 · Spring Boot 3.5.16 · Gradle 8.14.3

## 실행 결과

- Windows `gradlew.bat testClasses`: main/test 컴파일 성공.
- Windows `gradlew.bat test`: 33개 실행, 23개 통과, 10개 의도된 실패, skip 0개.
- Unix용 `gradlew`는 Windows Git Bash에서 실행 검증했습니다. Linux/macOS 실제 OS에서의 실행을 검증했다는 의미는 아닙니다.

| 테스트 클래스 | 실행 | 통과 | 의도된 실패 |
|---|---:|---:|---:|
| ActivityControllerTest | 16 | 16 | 0 |
| StarterInfrastructureTest | 6 | 6 | 0 |
| BusinessBoundaryTest | 1 | 1 | 0 |
| ActivityServiceTest | 8 | 0 | 8 |
| ActivityVolumeTest | 2 | 0 | 2 |

서비스의 `UnsupportedOperationException("TODO: implement activity feed")` 때문에 일반 비즈니스 테스트 9개가 실패합니다. 회원 부재 테스트 1개는 `MemberNotFoundException`을 기대했지만 같은 TODO 예외가 발생하여 assertion 실패로 표시됩니다. 컴파일, Spring context, fixture 입력, 입력 검증/예외 매핑, JDBC 관찰기 및 트랜잭션 경계 자체 검증은 통과했습니다.

## 검증 범위와 한계

- 핵심 메서드가 즉시 예외를 던지므로 비즈니스 테스트의 정상 응답 이후 assertion은 아직 실행되지 않습니다. 컴파일 검증과 별도 fixture/oracle·계측 자체 테스트, 코드 리뷰로 스타터 결함을 점검했습니다. 사용자 구현 후 전체 테스트 통과가 최종 완료 기준입니다.
- 두 대량 테스트는 각각 POST 10,000개와 COMMENT 40,000개의 입력 및 개수 확인을 마친 후 TODO 예외에 도달했습니다.
- JDBC 행 수 관찰기가 DTO/JDBC 결과와 JPA entity 적재를 관찰하고, 상한 초과를 검출하는 자체 테스트가 통과했습니다. DB 내부 scan/offset 비용은 측정하지 않으며 README에 한계를 명시했습니다.
- 비즈니스 fixture는 커밋 후 테스트 트랜잭션 밖에서 호출합니다. 직접 DataSource 연결에서도 데이터가 보임을 별도 테스트로 확인했습니다. 실패 후 정리와 다음 테스트의 빈 DB 검사도 수행했습니다.
- 최종 핵심 구현은 TODO 그대로입니다. 임시 정답 조회 구현은 넣지 않았습니다. Repository에도 정답 쿼리는 없습니다.
- 테스트 실행에서 JVM class-sharing 및 Gradle 9 관련 deprecation 안내가 나왔지만, 고정된 Gradle 8.14.3에서 컴파일/config/fixture 실패는 없었습니다.

## 기존 저장소 보존

작업 전 추적 파일의 SHA-256과 비교했습니다. 기존 파일 중 변경은 루트 README 과제 인덱스의 링크 한 줄뿐입니다. 다른 미션, 사용자 코드, 루트 `.gitignore`는 변경하지 않았습니다. 커밋이나 push는 수행하지 않았습니다.
