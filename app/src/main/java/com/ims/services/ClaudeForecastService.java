package com.ims.services;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.config.ImsProperties;
import com.ims.model.ForecastResult;
import com.ims.model.StockMovement;
import com.ims.repository.StockMovementLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Claude API-backed demand forecast (ims.forecast.provider=claude). Used
 * instead of Bedrock because the AWS Academy Learner Lab does not grant
 * Bedrock model access; the Claude API is plain HTTPS out through the NAT.
 *
 * The recent daily OUTBOUND demand series is sent to the model, which returns
 * a per-day forecast as JSON that is parsed into the result. On ANY failure
 * (no ANTHROPIC_API_KEY, network error, unparseable reply) the service
 * degrades to the statistical EWMA baseline, so /forecast never fails.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ims.forecast.provider", havingValue = "claude")
public class ClaudeForecastService implements ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeForecastService.class);
    private static final int HISTORY_LIMIT = 1000;
    private static final int MAX_HISTORY_DAYS = 60;

    private final StockMovementLedger ledger;
    private final EwmaForecastService fallback;
    private final ObjectMapper mapper;
    private final String model;
    private final AnthropicClient client; // null when no API key is configured

    public ClaudeForecastService(StockMovementLedger ledger, ObjectMapper mapper, ImsProperties props) {
        this.ledger = ledger;
        this.fallback = new EwmaForecastService(ledger);
        this.mapper = mapper;
        this.model = props.getForecast().getClaudeModel();

        AnthropicClient c = null;
        try {
            c = AnthropicOkHttpClient.fromEnv(); // reads ANTHROPIC_API_KEY
        } catch (Exception e) {
            log.warn("Claude forecast provider selected but no usable ANTHROPIC_API_KEY; "
                    + "falling back to EWMA for all forecasts: {}", e.getMessage());
        }
        this.client = c;
    }

    @Override
    public ForecastResult forecast(String sku, int days) {
        int horizon = days <= 0 ? 30 : days;
        if (client == null) {
            return fallback.forecast(sku, horizon);
        }
        try {
            Map<LocalDate, Integer> daily = dailyOutbound(sku);
            if (daily.isEmpty()) {
                return fallback.forecast(sku, horizon); // no demand history to reason over
            }

            String history = daily.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(", "));
            String prompt = """
                    You are a demand forecasting engine for a warehouse inventory system.
                    Historical daily outbound demand for SKU %s (date:units, oldest first, \
                    days with no sales are omitted and mean zero demand):
                    %s

                    Forecast the daily demand for the next %d days, accounting for the \
                    trend and any weekly pattern you can see. Respond with ONLY a JSON \
                    array of exactly %d non-negative numbers (units per day, in order, \
                    starting tomorrow). No explanation, no markdown fences.
                    """.formatted(sku, history, horizon, horizon);

            Message message = client.messages().create(MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(4096L)
                    .addUserMessage(prompt)
                    .build());

            String text = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining())
                    .trim();
            double[] values = parseForecast(text, horizon);

            List<ForecastResult.Point> points = new ArrayList<>(horizon);
            LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(1);
            for (int i = 0; i < horizon; i++) {
                double qty = Math.max(0, Math.round(values[i] * 100.0) / 100.0);
                points.add(new ForecastResult.Point(start.plusDays(i), qty));
            }
            return new ForecastResult(sku, "CLAUDE(" + model + ")", points);
        } catch (Exception e) {
            log.warn("Claude forecast failed for sku={}, falling back to EWMA: {}", sku, e.getMessage());
            return fallback.forecast(sku, horizon);
        }
    }

    /** Aggregate outbound qty per day (oldest first), capped to the recent window. */
    private Map<LocalDate, Integer> dailyOutbound(String sku) {
        Map<LocalDate, Integer> daily = new TreeMap<>();
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(MAX_HISTORY_DAYS);
        for (StockMovement m : ledger.outboundHistory(sku, HISTORY_LIMIT)) {
            LocalDate d = m.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            if (!d.isBefore(cutoff)) {
                daily.merge(d, m.getQty(), Integer::sum);
            }
        }
        return daily;
    }

    /** Parse the model reply into exactly {@code horizon} values (defensive against fences/prose). */
    private double[] parseForecast(String text, int horizon) throws Exception {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("model reply contains no JSON array");
        }
        double[] parsed = mapper.readValue(text.substring(start, end + 1), double[].class);
        if (parsed.length == 0) {
            throw new IllegalStateException("model returned an empty forecast");
        }
        // Tolerate a wrong-length reply: truncate or pad with the last value.
        double[] values = new double[horizon];
        for (int i = 0; i < horizon; i++) {
            values[i] = parsed[Math.min(i, parsed.length - 1)];
        }
        return values;
    }
}
