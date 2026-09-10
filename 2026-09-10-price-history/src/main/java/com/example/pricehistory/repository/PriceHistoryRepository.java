package com.example.pricehistory.repository;

import com.example.pricehistory.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    // TODO: 필요한 조회 메서드를 직접 설계하세요.
}
