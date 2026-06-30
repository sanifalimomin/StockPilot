package com.ims.repository;

import com.ims.model.PurchaseOrder;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @EntityGraph(attributePaths = "lines")
    Optional<PurchaseOrder> findWithLinesById(Long id);

    // Override the default findAll so the lazy `lines` collection is fetched
    // eagerly; the response mapper reads it after the session closes, which
    // otherwise throws LazyInitializationException on the list endpoint.
    @Override
    @EntityGraph(attributePaths = "lines")
    List<PurchaseOrder> findAll();
}
