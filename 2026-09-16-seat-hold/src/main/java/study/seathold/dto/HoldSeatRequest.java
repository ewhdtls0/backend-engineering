package study.seathold.dto;
import jakarta.validation.constraints.*;
public record HoldSeatRequest(@NotNull @Positive Long memberId) {}

