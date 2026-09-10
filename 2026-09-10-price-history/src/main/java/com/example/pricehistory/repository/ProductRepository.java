package com.example.pricehistory.repository;

import com.example.pricehistory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProductRepository extends JpaRepository<Product, Long> {
    // TODO: 필요한 조회 메서드를 직접 설계하세요.
}
