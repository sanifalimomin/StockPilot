package com.ims.controller;

import com.ims.services.ForecastService;
import com.ims.model.ForecastResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping
    public ForecastResult forecast(@RequestParam String sku,
                                   @RequestParam(defaultValue = "30") int days) {
        return forecastService.forecast(sku, days);
    }
}
