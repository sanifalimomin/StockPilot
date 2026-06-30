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

    @PostMapping("/valuation")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> valuation() {
        return service.generateValuation();
    }

    @GetMapping
    public List<ReportStore.ReportDescriptor> list() {
        return service.listReports();
    }
}
