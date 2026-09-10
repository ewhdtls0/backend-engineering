package com.example.pricehistory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ChangePriceRequest(
    @NotNull @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) BigDecimal price,
    @NotBlank @Size(max = 500) String reason
) {}
