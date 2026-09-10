package com.example.pricehistory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
public record PriceHistoryResponse(BigDecimal previousPrice, BigDecimal changedPrice,
                                   String reason, LocalDateTime changedAt) {}
