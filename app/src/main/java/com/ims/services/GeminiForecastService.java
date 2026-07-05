package com.ims.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ims.config.ImsProperties;
import com.ims.model.ForecastResult;
import com.ims.model.StockMovement;
import com.ims.repository.StockMovementLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Google Gemini-backed demand forecast (ims.forecast.provider=gemini).
 * Attractive for the Learner Lab because Google AI Studio issues FREE API
 * keys with a free usage tier — no billing account needed, unlike Bedrock
 * (blocked in the lab) or the Claude API (prepaid credits).
 *
 * Calls the public REST endpoint (generativelanguage.googleapis.com) with
 * Java's built-in HTTP client — plain HTTPS out through the NAT, no SDK.
 * Same contract as the other providers: recent daily OUTBOUND demand goes to
 * the model, which returns a per-day forecast as a JSON array. On ANY failure
 * (no GEMINI_API_KEY, rate limit, unparseable reply) it degrades to the
 * statistical EWMA baseline, so /forecast never fails.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ims.forecast.provider", havingValue = "gemini")
public class GeminiForecastService implements ForecastService {

    private static final Logger log = LoggerFactory.getLogger(GeminiForecastService.class);
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final int HISTORY_LIMIT = 1000;
    private static final int MAX_HISTORY_DAYS = 60;

    private final StockMovementLedger ledger;
    private final EwmaForecastService fallback;
    private final ObjectMapper mapper;
    private final String model;
    private final String apiKey; // empty when not configured -> EWMA only
    private final HttpClient http;

    public GeminiForecastService(StockMovementLedger ledger, ObjectMapper mapper, ImsProperties props) {
        this.ledger = ledger;
        this.fallback = new EwmaForecastService(ledger);
        this.mapper = mapper;
        this.model = props.getForecast().getGeminiModel();
        this.apiKey = System.getenv().getOrDefault("GEMINI_API_KEY", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        if (apiKey.isBlank()) {
            log.warn("Gemini forecast provider selected but GEMINI_API_KEY is not set; "
                    + "falling back to EWMA for all forecasts.");
        }
    }

    @Override
    public ForecastResult forecast(String sku, int days) {
        int horizon = days <= 0 ? 30 : days;
        if (apiKey.isBlank()) {
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

            ObjectNode body = mapper.createObjectNode();
            body.putArray("contents").addObject()
                    .putArray("parts").addObject()
                    .put("text", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(model)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Gemini API returned HTTP " + response.statusCode());
            }

            String text = extractText(mapper.readTree(response.body()));
            double[] values = parseForecast(text, horizon);

            List<ForecastResult.Point> points = new ArrayList<>(horizon);
            LocalDate start = LocalDate.now(ZoneOffset.UTC).plusDays(1);
            for (int i = 0; i < horizon; i++) {
                double qty = Math.max(0, Math.round(values[i] * 100.0) / 100.0);
                points.add(new ForecastResult.Point(start.plusDays(i), qty));
            }
            return new ForecastResult(sku, "GEMINI(" + model + ")", points);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.forecast(sku, horizon);
        } catch (Exception e) {
            log.warn("Gemini forecast failed for sku={}, falling back to EWMA: {}", sku, e.getMessage());
            return fallback.forecast(sku, horizon);
        }
    }

    /** Concatenate the text parts of the first candidate. */
    private String extractText(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
            sb.append(part.path("text").asText(""));
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini reply contained no text (blocked or empty candidate)");
        }
        return text;
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
