package study.cartsync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.cartsync.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {}
