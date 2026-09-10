package com.example.pricehistory.service;

import com.example.pricehistory.dto.*;
import com.example.pricehistory.repository.*;
import org.springframework.stereotype.Service;
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
    // TODO: 트랜잭션 경계, 동일 가격 판단, 변경 책임, 저장 방식을 직접 결정하세요.
    public void changePrice(Long productId, ChangePriceRequest request) {
        throw new UnsupportedOperationException("TODO: changePrice");
    }
    // TODO: 선택 기간 조건, 정렬, DTO 변환, 존재하지 않는 상품 처리를 구현하세요.
    public List<PriceHistoryResponse> getPriceHistories(Long productId, LocalDateTime from, LocalDateTime to) {
        throw new UnsupportedOperationException("TODO: getPriceHistories");
    }
}
