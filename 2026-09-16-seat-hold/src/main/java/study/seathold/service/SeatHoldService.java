package study.seathold.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import study.seathold.domain.Seat;
import study.seathold.domain.SeatHold;
import study.seathold.domain.SeatHoldStatus;
import study.seathold.domain.SeatStatus;
import study.seathold.dto.ConfirmSeatHoldRequest;
import study.seathold.dto.HoldSeatRequest;
import study.seathold.dto.SeatHoldResponse;
import study.seathold.exception.SeatHoldException;
import study.seathold.repository.SeatHoldRepository;
import study.seathold.repository.SeatRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SeatHoldService {
    private final SeatRepository seats;
    private final SeatHoldRepository holds;
    private final Clock clock;
    public SeatHoldService(SeatRepository seats, SeatHoldRepository holds, Clock clock) {
        this.seats = seats;
        this.holds = holds;
        this.clock = clock;
    }

    @Transactional
    public SeatHoldResponse hold(Long seatId, HoldSeatRequest request) {
        // TODO: 시간 공급, 유효성, 만료 row, 동시성, 트랜잭션 경계를 직접 설계하세요.
        if (request == null || request.memberId() == null || request.memberId() <= 0) {
            throw new SeatHoldException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        Seat seat = seats.findByIdWithLock(seatId)
                .orElseThrow(() -> new SeatHoldException(HttpStatus.NOT_FOUND, "해당하는 좌석이 없습니다."));

        if (seat.getStatus().equals(SeatStatus.RESERVED)) {
            throw new SeatHoldException(HttpStatus.CONFLICT, "이미 선점된 좌석입니다.");
        }

        LocalDateTime now = LocalDateTime.now(clock);

        List<SeatHold> seatHolds = holds.findAllAvailableBySeatId(seatId, now);
        boolean available = seatHolds.isEmpty();

        if (!available) {
            throw new SeatHoldException(HttpStatus.CONFLICT, "이미 선점된 좌석입니다.");
        }

        SeatHold savedHold = holds.save(new SeatHold(
                seat,
                request.memberId(),
                SeatHoldStatus.HELD,
                now.plusMinutes(5),
                now
        ));

        return new SeatHoldResponse(
                savedHold.getId(),
                seatId,
                savedHold.getMemberId(),
                savedHold.getStatus(),
                savedHold.getExpiresAt()
        );
    }

    @Transactional
    public SeatHoldResponse confirm(Long holdId, ConfirmSeatHoldRequest request) {

        if (holdId == null || request == null || request.memberId() == null || request.memberId() <= 0) {
            throw new SeatHoldException(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");
        }

        SeatHold seatHold = holds.findByIdWithSeat(holdId)
                .orElseThrow(() -> new SeatHoldException(HttpStatus.NOT_FOUND, "해당하는 선점이 없습니다."));

        if (seatHold.getSeat().getStatus().equals(SeatStatus.RESERVED)) {
            throw new SeatHoldException(HttpStatus.CONFLICT, "이미 선점된 좌석입니다.");
        }

        if (seatHold.getExpiresAt().isEqual(LocalDateTime.now(clock)) || seatHold.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw new SeatHoldException(HttpStatus.CONFLICT, "이미 만료된 선점입니다.");
        }

        if (!seatHold.getMemberId().equals(request.memberId())) {
            throw new SeatHoldException(HttpStatus.FORBIDDEN, "선점 소유자가 다릅니다.");
        }

        seatHold.confirmed();

        return new SeatHoldResponse(
                holdId,
                seatHold.getSeat().getId(),
                seatHold.getMemberId(),
                seatHold.getStatus(),
                seatHold.getExpiresAt()
        );
    }
}

