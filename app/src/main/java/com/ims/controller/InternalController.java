package com.ims.controller;

import com.ims.services.ReorderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal operations endpoint (manually trigger the reorder scan for demos).
 */
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    private final ReorderService reorderService;

    public InternalController(ReorderService reorderService) {
        this.reorderService = reorderService;
    }

    @PostMapping("/reorder-scan")
    public Map<String, Object> reorderScan() {
        int openAlerts = reorderService.scanAll();
        return Map.of("status", "completed", "openAlerts", openAlerts);
    }
}
