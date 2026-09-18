package study.activityfeed.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.activityfeed.domain.Post;

public interface PostRepository extends JpaRepository<Post, Long> {
}
