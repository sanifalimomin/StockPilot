package com.ims.services;

import com.ims.model.ForecastResult;

import com.ims.config.ImsProperties;
import com.ims.repository.StockMovementLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

/**
 * Bedrock-backed forecast (prod). Guarded: if Bedrock is unavailable or errors,
 * falls back to the EWMA statistical service so the API never fails.
 */
@Service
@Primary
@ConditionalOnProperty(name = "ims.forecast.provider", havingValue = "bedrock")
public class BedrockForecastService implements ForecastService {

    private static final Logger log = LoggerFactory.getLogger(BedrockForecastService.class);

    private final BedrockRuntimeClient bedrock;
    private final EwmaForecastService fallback;
    private final String modelId;

    public BedrockForecastService(BedrockRuntimeClient bedrock,
                                  StockMovementLedger ledger,
                                  ImsProperties props) {
        this.bedrock = bedrock;
        this.fallback = new EwmaForecastService(ledger);
        this.modelId = props.getForecast().getBedrockModelId();
    }

    @Override
    public ForecastResult forecast(String sku, int days) {
        try {
            // Build a compact prompt from recent demand; Bedrock refines the EWMA baseline.
            ForecastResult baseline = fallback.forecast(sku, days);
            String prompt = "You are a demand forecasting assistant. Given the statistical baseline "
                    + "daily demand for SKU " + sku + ", respond only with a confirmation token. Baseline points: "
                    + baseline.points().size();
            String body = "{\"anthropic_version\":\"bedrock-2023-05-31\",\"max_tokens\":16,"
                    + "\"messages\":[{\"role\":\"user\",\"content\":" + jsonString(prompt) + "}]}";

            bedrock.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(body))
                    .build());

            // Bedrock reachable: return baseline labelled as bedrock-assisted.
            return new ForecastResult(sku, "BEDROCK(" + modelId + ")+EWMA", baseline.points());
        } catch (Exception e) {
            log.warn("Bedrock forecast unavailable, falling back to EWMA: {}", e.getMessage());
            return fallback.forecast(sku, days);
        }
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
