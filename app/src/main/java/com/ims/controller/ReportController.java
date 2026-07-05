package com.ims.controller;

import com.ims.repository.ReportStore;
import com.ims.services.ReportService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/daily")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, String>> daily() {
        return service.generateDailyReports();
    }

    @PostMapping("/valuation")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> valuation() {
        return service.generateValuation();
    }

    @PostMapping("/low-stock")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> lowStock() {
        return service.generateLowStock();
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> movements() {
        return service.generateMovements();
    }

    @GetMapping
    public List<ReportStore.ReportDescriptor> list() {
        return service.listReports();
    }
}
