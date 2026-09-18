package study.activityfeed.support;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import study.activityfeed.domain.*;
import study.activityfeed.dto.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@Import(JdbcReadProbe.Configuration.class)
@Transactional
public abstract class DatabaseTest {
    protected static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 18, 12, 0);
    @Autowired protected EntityManager em;
    @Autowired protected JdbcTemplate jdbc;

    protected Member member(String name) {
        Member member = new Member(name);
        em.persist(member);
        return member;
    }

    protected ActivityResponse post(Member member, int second) {
        Post post = new Post(member, "post-" + second, BASE.plusSeconds(second));
        em.persist(post);
        return new ActivityResponse(ActivityType.POST, post.getId(), post.getTitle(), post.getCreatedAt());
    }

    protected ActivityResponse comment(Member member, int second) {
        Comment comment = new Comment(member, "comment-" + second, BASE.plusSeconds(second));
        em.persist(comment);
        return new ActivityResponse(ActivityType.COMMENT, comment.getId(), comment.getContent(), comment.getCreatedAt());
    }

    protected void cold() { em.flush(); em.clear(); }

    protected static List<ActivityResponse> newest(List<ActivityResponse> input) {
        return input.stream().sorted(Comparator.comparing(ActivityResponse::createdAt).reversed()).toList();
    }

    protected static String key(ActivityResponse activity) {
        return activity.type() + ":" + activity.activityId();
    }

    protected List<ActivityResponse> fortyFive(Member member) {
        List<ActivityResponse> expected = new ArrayList<>();
        // Deliberate imbalance across page boundaries; never assume each source contributes equally.
        for (int i = 44; i >= 20; i--) expected.add(post(member, i));
        for (int i = 19; i >= 0; i--) expected.add(comment(member, i));
        return expected;
    }
}
