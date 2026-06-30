package com.ims.controller;

import com.ims.repository.LowStockAlertRepository;
import com.ims.model.LowStockAlert;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final LowStockAlertRepository repo;

    public AlertController(LowStockAlertRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<LowStockAlert> list(@RequestParam(required = false, defaultValue = "false") boolean resolved) {
        return repo.findByResolved(resolved);
    }
}
