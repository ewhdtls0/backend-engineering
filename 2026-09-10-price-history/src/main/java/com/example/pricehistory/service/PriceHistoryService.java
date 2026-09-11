package com.example.pricehistory.service;

import com.example.pricehistory.domain.PriceHistory;
import com.example.pricehistory.domain.Product;
import com.example.pricehistory.dto.ChangePriceRequest;
import com.example.pricehistory.dto.PriceHistoryResponse;
import com.example.pricehistory.exception.ProductNotFoundException;
import com.example.pricehistory.repository.PriceHistoryRepository;
import com.example.pricehistory.repository.ProductRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PriceHistoryService {
    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    public PriceHistoryService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }
    @Transactional
    public void changePrice(Long productId, ChangePriceRequest request) {
        Product productEntity = getProductEntity(productId);

        int compareFlag = productEntity.getPrice().compareTo(request.price());

        if (compareFlag != 0) {
            LocalDateTime now = LocalDateTime.now();
            BigDecimal oldPrice = productEntity.getPrice();

            priceHistoryRepository.save(
                    new PriceHistory(productEntity, oldPrice, request.price(), request.reason(), now)
            );

            productEntity.changePrice(request.price(), now);
        }
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResponse> getPriceHistories(Long productId, LocalDateTime from, LocalDateTime to) {
        Product productEntity = getProductEntity(productId);

        return priceHistoryRepository.findAllByProductId(productEntity.getId(), from, to)
                .stream()
                .map(priceHistory -> new PriceHistoryResponse(
                        priceHistory.getPreviousPrice(),
                        priceHistory.getChangedPrice(),
                        priceHistory.getReason(),
                        priceHistory.getChangedAt()))
                .toList();

    }

    @NonNull
    private Product getProductEntity(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
