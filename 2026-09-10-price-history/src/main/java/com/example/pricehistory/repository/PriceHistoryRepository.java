package com.example.pricehistory.repository;

import com.example.pricehistory.domain.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
    // TODO: 필요한 조회 메서드를 직접 설계하세요.

    @Query("select ph from PriceHistory ph where ph.product.id = :productId and (:from is null or ph.changedAt >= :from) and (:to is null or ph.changedAt <= :to) order by ph.changedAt desc, ph.id desc")
    List<PriceHistory> findAllByProductId(
            @Param("productId") Long productId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

}
