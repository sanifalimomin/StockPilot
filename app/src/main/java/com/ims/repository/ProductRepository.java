package com.ims.repository;

import com.ims.model.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    // CAST(:q AS string) gives PostgreSQL a typed parameter; without it a null
    // :q is bound as an untyped value (bytea) and LOWER(...) fails at runtime
    // with "function lower(bytea) does not exist" on the unfiltered list call.
    @Query("""
            SELECT p FROM Product p
            WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:supplierId IS NULL OR p.supplierId = :supplierId)
              AND (:q IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
                              OR LOWER(p.sku) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
            """)
    List<Product> search(@Param("categoryId") Long categoryId,
                         @Param("supplierId") Long supplierId,
                         @Param("q") String q);
}
