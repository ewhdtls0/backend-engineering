package com.example.pricehistory.controller;

import com.example.pricehistory.dto.*;
import com.example.pricehistory.service.PriceHistoryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}")
public class ProductPriceController {
    private final PriceHistoryService service;
    public ProductPriceController(PriceHistoryService service) { this.service = service; }
    @PatchMapping("/price")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePrice(@PathVariable Long productId, @Valid @RequestBody ChangePriceRequest request) {
        service.changePrice(productId, request);
    }
    @GetMapping("/price-histories")
    public List<PriceHistoryResponse> getPriceHistories(@PathVariable Long productId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return service.getPriceHistories(productId, from, to);
    }
}
