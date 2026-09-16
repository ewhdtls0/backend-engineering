package study.notification;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** 로컬 실습용. 실제 알림을 발송하지 않고 호출을 기록합니다. */
@Component
public class DemoNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(DemoNotificationSender.class);
    public void send(Long memberId, String message) {
        log.info("Demo notification memberId={}, message={}", memberId, message);
    }
}
