package study.seathold.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "seat_holds")
public class SeatHold {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;
    @Column(nullable = false)
    private Long memberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SeatHoldStatus status;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    protected SeatHold() {}
    public SeatHold(Seat seat, Long memberId, SeatHoldStatus status,
                    LocalDateTime expiresAt, LocalDateTime createdAt) {
        this.seat = seat;
        this.memberId = memberId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public Seat getSeat() { return seat; }
    public Long getMemberId() { return memberId; }
    public SeatHoldStatus getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    // TODO: 상태 전이와 시간 관련 책임을 설계하세요.

    public void confirmed() {
        this.status = SeatHoldStatus.CONFIRMED;
        this.seat.changeStatus(SeatStatus.RESERVED);
    }
}

