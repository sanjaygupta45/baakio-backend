package com.baakio.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OpenRouterScanExtractor implements ScanExtractor {
    private static final String EXTRACTION_PROMPT = """
            Read this single customer purchase bill for an Indian neighbourhood shop.
            Return JSON fields is_new_purchase, occurred_on (ISO yyyy-MM-dd or null), description,
            discount (decimal string or null), written_total (decimal string or null), and items.
            Each item must contain name, quantity, unit, unit_price, written_line_total,
            confidence_name, confidence_quantity and confidence_unit_price.
            Keep product names in the script used on the bill. Do not translate or guess.
            Use null for any unreadable or absent field. Confidence values are from 0 to 1.
            is_new_purchase is true only when this is a single new purchase bill. It is false for
            a ledger, statement, old balance, closing balance, account summary, or payment receipt
            without purchase line items. Never treat an amount due or prior balance as a purchase.
            Do not infer that a customer paid; payment requires explicit shopkeeper confirmation.
            Return only a JSON object matching the requested fields.
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public OpenRouterScanExtractor(RestClient.Builder builder, ObjectMapper mapper,
                                   @Value("${baakio.scan.openrouter.base-url:https://openrouter.ai/api/v1}") String baseUrl,
                                   @Value("${baakio.scan.openrouter.api-key:}") String apiKey,
                                   @Value("${baakio.scan.openrouter.model:qwen/qwen3-vl-8b-instruct}") String model) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ScanExtraction extract(byte[] content, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenRouter bill scanning is not configured.");
        }
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(content);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", EXTRACTION_PROMPT),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0);
        JsonNode response = client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve().body(JsonNode.class);
        JsonNode answer = response == null ? null : response.path("choices").path(0).path("message").path("content");
        if (answer == null || !answer.isTextual() || answer.asString().isBlank()) {
            throw new IllegalStateException("OpenRouter returned no structured bill draft.");
        }
        try {
            return mapper.readValue(answer.asString(), ScanExtraction.class);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenRouter returned an invalid structured bill draft.", exception);
        }
    }
}
