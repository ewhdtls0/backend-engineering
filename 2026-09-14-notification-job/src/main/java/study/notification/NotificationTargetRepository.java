package study.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationTargetRepository extends JpaRepository<NotificationTarget, Long> {
    List<NotificationTarget> findByJobIdOrderByIdAsc(Long jobId);
}
