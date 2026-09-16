package study.notification;

public interface NotificationSender {
    void send(Long memberId, String message);
}
