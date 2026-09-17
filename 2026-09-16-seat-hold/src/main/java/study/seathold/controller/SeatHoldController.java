package study.seathold.controller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import study.seathold.dto.*;
import study.seathold.service.SeatHoldService;
@RestController
@RequestMapping("/api")
public class SeatHoldController {
    private final SeatHoldService service;
    public SeatHoldController(SeatHoldService service) { this.service = service; }
    @PostMapping("/seats/{seatId}/holds") @ResponseStatus(HttpStatus.CREATED)
    public SeatHoldResponse hold(@PathVariable Long seatId, @Valid @RequestBody HoldSeatRequest request) {
        return service.hold(seatId, request);
    }
    @PostMapping("/seat-holds/{holdId}/confirm")
    public SeatHoldResponse confirm(@PathVariable Long holdId, @Valid @RequestBody ConfirmSeatHoldRequest request) {
        return service.confirm(holdId, request);
    }
}

