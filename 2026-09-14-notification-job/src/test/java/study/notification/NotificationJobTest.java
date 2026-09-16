package study.notification;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationJobTest {
    @Autowired NotificationJobService service;
    @Autowired NotificationJobRepository jobs;
    @Autowired NotificationTargetRepository targets;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired FakeSender sender;
    Long jobId;
    static final String MESSAGE = "서비스 점검 안내";

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeSender fakeSender() { return new FakeSender(); }
    }
    static class FakeSender implements NotificationSender {
        record Call(Long memberId, String message) {}
        final List<Call> calls = new CopyOnWriteArrayList<>();
        final Set<Long> failures = new HashSet<>();
        public void send(Long memberId, String message) {
            calls.add(new Call(memberId, message));
            if (failures.contains(memberId)) throw new IllegalStateException("provider rejected member " + memberId);
        }
    }
    @BeforeEach void fixture() {
        sender.calls.clear(); sender.failures.clear();
        // 실행 테스트는 createJob TODO와 독립적입니다. fixture는 실제로 커밋됩니다.
        jobId = new TransactionTemplate(manager).execute(status -> {
            targets.deleteAllInBatch(); jobs.deleteAllInBatch();
            var job = jobs.saveAndFlush(new NotificationJob(MESSAGE, 3));
            targets.saveAllAndFlush(List.of(new NotificationTarget(job.getId(), 33L),
                new NotificationTarget(job.getId(), 11L), new NotificationTarget(job.getId(), 22L)));
            return job.getId();
        });
        assertThat(jdbc.queryForObject("select count(*) from notification_targets where job_id=?", Long.class, jobId)).isEqualTo(3);
    }
    CreateJobRequest request(Long... members) { return new CreateJobRequest(List.of(members), MESSAGE); }
    void error(Runnable action, MissionException.Code code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(MissionException.class,
            e -> assertThat(e.getCode()).isEqualTo(code));
    }
    List<Map<String,Object>> jobRows() { return jdbc.queryForList("select * from notification_jobs order by id"); }
    List<Map<String,Object>> targetRows() { return jdbc.queryForList("select * from notification_targets order by id"); }

    @Test void committedFixtureIsQueryableThroughApi() throws Exception {
        mvc.perform(get("/api/notification-jobs/{id}", jobId)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.successCount").value(0))
            .andExpect(jsonPath("$.failureCount").value(0))
            .andExpect(jsonPath("$.targets.length()").value(3));
    }
    @Test void createsJobAndEveryTargetWithoutSending() {
        var result = service.createJob(request(200L, 100L));
        assertThat(result.jobId()).isNotNull().isNotEqualTo(jobId);
        assertThat(result.status()).isEqualTo(JobStatus.PENDING);
        var stored = jobs.findById(result.jobId()).orElseThrow();
        assertThat(stored.getMessage()).isEqualTo(MESSAGE);
        assertThat(stored.getTotalCount()).isEqualTo(2);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(jobs.count()).isEqualTo(2);
        assertThat(targets.findByJobIdOrderByIdAsc(result.jobId()))
            .extracting(NotificationTarget::getMemberId).containsExactly(200L, 100L);
        assertThat(targets.findByJobIdOrderByIdAsc(result.jobId())).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(TargetStatus.PENDING);
            assertThat(t.getFailureReason()).isNull();
        });
        assertThat(sender.calls).isEmpty();
    }
    void assertCompleted(Set<Long> failed, JobStatus expected) {
        Long otherId = new TransactionTemplate(manager).execute(s -> {
            var other = jobs.saveAndFlush(new NotificationJob("다른 작업", 1));
            targets.saveAndFlush(new NotificationTarget(other.getId(), 99L));
            return other.getId();
        });
        var otherJobBefore = jdbc.queryForList("select * from notification_jobs where id=?", otherId);
        var otherTargetsBefore = jdbc.queryForList("select * from notification_targets where job_id=?", otherId);
        sender.failures.addAll(failed);
        service.execute(jobId);
        assertThat(sender.calls).extracting(FakeSender.Call::memberId).containsExactly(33L, 11L, 22L);
        assertThat(jdbc.queryForList("select * from notification_jobs where id=?", otherId)).isEqualTo(otherJobBefore);
        assertThat(jdbc.queryForList("select * from notification_targets where job_id=?", otherId)).isEqualTo(otherTargetsBefore);
        assertThat(sender.calls).allSatisfy(c -> assertThat(c.message()).isEqualTo(MESSAGE));
        // JDBC observes committed state independently of the service's persistence context.
        assertThat(jdbc.queryForObject("select status from notification_jobs where id=?", String.class, jobId))
            .isEqualTo(expected.name());
        var rows = targets.findByJobIdOrderByIdAsc(jobId);
        assertThat(rows).hasSize(3).allSatisfy(t -> {
            if (failed.contains(t.getMemberId())) {
                assertThat(t.getStatus()).isEqualTo(TargetStatus.FAILED);
                assertThat(t.getFailureReason()).isNotBlank();
            } else {
                assertThat(t.getStatus()).isEqualTo(TargetStatus.SUCCESS);
                assertThat(t.getFailureReason()).isNull();
            }
        });
        var result = service.getJob(jobId);
        assertThat(result.status()).isEqualTo(expected);
        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(3 - failed.size());
        assertThat(result.failureCount()).isEqualTo(failed.size());
        assertThat(result.targets()).hasSize(3).allSatisfy(t -> {
            assertThat(t.status()).isEqualTo(failed.contains(t.memberId()) ? TargetStatus.FAILED : TargetStatus.SUCCESS);
            if (failed.contains(t.memberId())) assertThat(t.failureReason()).isNotBlank();
        });
    }
    @Test
    void allSuccess() {
        assertCompleted(Set.of(), JobStatus.COMPLETED);
    }
    @Test void middleFailureStillSendsLast() { assertCompleted(Set.of(11L), JobStatus.COMPLETED_WITH_FAILURES); }
    @Test void firstFailureStillSendsRemaining() { assertCompleted(Set.of(33L), JobStatus.COMPLETED_WITH_FAILURES); }
    @Test void allFailureIsFailedJob() { assertCompleted(Set.of(11L,22L,33L), JobStatus.FAILED); }
    @Test void duplicateMembersFailAtomically() {
        var beforeJobs = jobRows(); var beforeTargets = targetRows();
        error(() -> service.createJob(request(100L,200L,100L)), MissionException.Code.INVALID_REQUEST);
        assertThat(jobRows()).isEqualTo(beforeJobs);
        assertThat(targetRows()).isEqualTo(beforeTargets);
        assertThat(sender.calls).isEmpty();
    }
    @Test void serviceRejectsEmptyMembers() {
        var before = jobRows();
        error(() -> service.createJob(request()), MissionException.Code.INVALID_REQUEST);
        assertThat(jobRows()).isEqualTo(before);
        assertThat(targets.count()).isEqualTo(3);
    }
    @Test void serviceRejectsBlankMessage() {
        var before = targetRows();
        error(() -> service.createJob(new CreateJobRequest(List.of(1L), "  ")), MissionException.Code.INVALID_REQUEST);
        assertThat(targetRows()).isEqualTo(before);
        assertThat(jobs.count()).isEqualTo(1);
    }
    @Test void nonexistentExecutionDoesNotSend() {
        error(() -> service.execute(Long.MAX_VALUE), MissionException.Code.NOT_FOUND);
        assertThat(sender.calls).isEmpty();
        assertThat(service.getJob(jobId).status()).isEqualTo(JobStatus.PENDING);
    }
    @Test void nonexistentQueryIs404() throws Exception {
        mvc.perform(get("/api/notification-jobs/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
    @ParameterizedTest @ValueSource(strings = {
        "{\"memberIds\":[],\"message\":\"hello\"}",
        "{\"memberIds\":[1],\"message\":\" \"}",
        "{\"memberIds\":[null],\"message\":\"hello\"}",
        "{\"memberIds\":[0],\"message\":\"hello\"}", "{}"})
    void httpValidationIs400AndAtomic(String body) throws Exception {
        var beforeJobs = jobRows(); var beforeTargets = targetRows();
        mvc.perform(post("/api/notification-jobs").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jobRows()).isEqualTo(beforeJobs); assertThat(targetRows()).isEqualTo(beforeTargets);
    }
    @Test void httpCreateReturns201WithJobId() throws Exception {
        mvc.perform(post("/api/notification-jobs").contentType("application/json")
            .content("{\"memberIds\":[100,200],\"message\":\"hello\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.jobId").isNumber())
            .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jobs.count()).isEqualTo(2); assertThat(targets.count()).isEqualTo(5);
    }
    @Test void httpExecutePersistsResult() throws Exception {
        mvc.perform(post("/api/notification-jobs/{id}/execute", jobId)).andExpect(status().isNoContent());
        assertThat(service.getJob(jobId).successCount()).isEqualTo(3);
        assertThat(sender.calls).hasSize(3);
    }
}
