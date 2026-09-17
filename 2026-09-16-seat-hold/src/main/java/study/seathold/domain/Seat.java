package study.seathold.domain;
import jakarta.persistence.*;
@Entity
@Table(name = "seats")
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String seatNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private SeatStatus status;
    protected Seat() {}
    public Seat(String seatNumber, SeatStatus status) {
        this.seatNumber = seatNumber;
        this.status = status;
    }
    public Long getId() { return id; }
    public String getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }
    public void changeStatus(SeatStatus status) {
        this.status = status;
    }
}

