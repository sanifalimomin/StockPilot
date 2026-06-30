package com.ims.services;

import com.ims.model.ForecastResult;

/** Demand forecast port. Impls: EwmaForecastService (default), BedrockForecastService (prod). */
public interface ForecastService {
    ForecastResult forecast(String sku, int days);
}
