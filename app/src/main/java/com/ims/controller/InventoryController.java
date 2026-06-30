package com.ims.controller;

import com.ims.services.InventoryService;
import com.ims.model.InventoryRowDto;
import com.ims.model.ConsolidatedStockDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<InventoryRowDto> rows(@RequestParam(required = false) Long warehouseId,
                                      @RequestParam(required = false) String sku) {
        return service.rows(warehouseId, sku);
    }

    @GetMapping("/consolidated")
    public ConsolidatedStockDto consolidated(@RequestParam String sku) {
        return service.consolidated(sku);
    }
}
