# Starter verification — 2026-09-16

## Environment
- Java: installed Temurin 21.0.12.1
- Spring Boot 3.5.16 / Gradle 8.14.3 / H2 via Boot dependency management
- Windows gradlew.bat and Unix gradlew (Git Bash) executed. Native Linux/macOS was not available for execution.

## Actual results
- gradlew.bat testClasses: PASS (main and test compilation).
- ./gradlew testClasses test --tests '*InfrastructureTest': PASS, 5 tests.
- gradlew.bat test: 16 tests, 5 passed, 11 failed, 0 skipped.
- XML report: build/test-results/test/TEST-study.seathold.SeatHoldCoreTest.xml
- HTML report: build/reports/tests/test/index.html

## Failure classification
9 core cases fail directly or through ServletException/ExecutionException because hold or confirm throws UnsupportedOperationException (TODO).
2 rollback cases fail the explicit 'must reach injected UPDATE fault' assertion because confirm is TODO and performs no UPDATE. They do not falsely count an arbitrary exception as proof of rollback.
No compile, Spring context, configuration or fixture error was observed.

## Infrastructure evidence
Context, committed fixtures, exact Clock boundary, positive-member validation, both H2 UPDATE fault triggers, and simultaneous two-connection visibility all passed.
Fixtures are not inside a test-wide transaction. Workers receive committed data. Assertions query the database independently.
All core tests are enabled and contain assertions. No core solution was temporarily installed or shipped.
A read-only independent source review found no blocking defect.

## Limits
Since service implementation is intentionally absent, later success-path assertions have not been executed against a completed solution. These results verify the starter's infrastructure and classify current failures; they are not a claim that the exercise is solved.
A single concurrent-start test and H2 do not prove all schedules, production-database behavior or multi-instance correctness. Repeat with the chosen implementation and target database.
No Thread.sleep, production failure hooks, service transaction boundaries or locking solution was supplied.

## Preservation
Only today's new project and additive study root README/.gitignore changes were made. The existing repository was not initialized, reset, committed or staged. An independently changing PaymentService in the previous assignment was left untouched.