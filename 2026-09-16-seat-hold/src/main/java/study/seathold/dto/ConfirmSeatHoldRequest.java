package study.seathold.dto;
import jakarta.validation.constraints.*;
public record ConfirmSeatHoldRequest(@NotNull @Positive Long memberId) {}

