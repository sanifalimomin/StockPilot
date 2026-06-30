package com.ims.repository;

import com.ims.model.InventoryLevel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {

    Optional<InventoryLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<InventoryLevel> findByProductId(Long productId);

    List<InventoryLevel> findByWarehouseId(Long warehouseId);
}
