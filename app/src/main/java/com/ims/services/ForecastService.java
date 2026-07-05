package com.ims.services;

import com.ims.model.ForecastResult;

public interface ForecastService {
    ForecastResult forecast(String sku, int days);
}
