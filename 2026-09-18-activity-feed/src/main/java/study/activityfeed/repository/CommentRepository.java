package study.activityfeed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.activityfeed.domain.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}
