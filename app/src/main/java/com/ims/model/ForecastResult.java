package com.ims.model;

import java.time.LocalDate;
import java.util.List;

public record ForecastResult(String sku, String method, List<Point> points) {

    public record Point(LocalDate date, double qty) {
    }
}
