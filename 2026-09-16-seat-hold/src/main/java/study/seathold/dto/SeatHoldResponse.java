package study.seathold.dto;
import java.time.LocalDateTime;
import study.seathold.domain.SeatHoldStatus;
public record SeatHoldResponse(Long holdId, Long seatId, Long memberId,
                               SeatHoldStatus status, LocalDateTime expiresAt) {}

