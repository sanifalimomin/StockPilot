package com.ims.services;

import com.ims.model.ForecastResult;

import com.ims.model.StockMovement;
import com.ims.repository.StockMovementLedger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@ConditionalOnProperty(name = "ims.forecast.provider", havingValue = "ewma", matchIfMissing = true)
public class EwmaForecastService implements ForecastService {

    private static final double ALPHA = 0.4;
    private static final int HISTORY_LIMIT = 1000;

    private final StockMovementLedger ledger;

    public EwmaForecastService(StockMovementLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public ForecastResult forecast(String sku, int days) {
        int horizon = days <= 0 ? 30 : days;
        List<StockMovement> history = ledger.outboundHistory(sku, HISTORY_LIMIT);

        Map<LocalDate, Integer> dailyDemand = new TreeMap<>();
        for (StockMovement m : history) {
            LocalDate d = m.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            dailyDemand.merge(d, m.getQty(), Integer::sum);
        }

        double ewma;
        if (dailyDemand.isEmpty()) {
            ewma = 0.0;
        } else {

            List<Integer> series = new ArrayList<>(dailyDemand.values());
            ewma = series.get(0);
            for (int i = 1; i < series.size(); i++) {
                ewma = ALPHA * series.get(i) + (1 - ALPHA) * ewma;
            }
        }

        double rounded = Math.round(ewma * 100.0) / 100.0;
        List<ForecastResult.Point> points = new ArrayList<>(horizon);
        LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        for (int i = 0; i < horizon; i++) {
            points.add(new ForecastResult.Point(start.plusDays(i), rounded));
        }
        return new ForecastResult(sku, "EWMA(alpha=" + ALPHA + ")", points);
    }
}
