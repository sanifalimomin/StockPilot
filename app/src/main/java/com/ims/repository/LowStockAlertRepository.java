package com.ims.repository;

import com.ims.model.LowStockAlert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {

    List<LowStockAlert> findByResolved(boolean resolved);

    Optional<LowStockAlert> findFirstByProductIdAndWarehouseIdAndResolvedFalse(Long productId, Long warehouseId);
}
