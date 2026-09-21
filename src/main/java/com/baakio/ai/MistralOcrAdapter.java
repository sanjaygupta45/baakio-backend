package com.baakio.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Component
public class MistralOcrAdapter implements ScanExtractor {
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
            """;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public MistralOcrAdapter(RestClient.Builder builder, ObjectMapper mapper,
                             @Value("${baakio.scan.mistral.base-url:https://api.mistral.ai}") String baseUrl,
                             @Value("${baakio.scan.mistral.api-key:}") String apiKey,
                             @Value("${baakio.scan.mistral.model:mistral-ocr-latest}") String model) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ScanExtraction extract(byte[] content, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("Mistral OCR is not configured.");
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(content);
        Map<String, Object> body = Map.of(
                "model", model,
                "document", Map.of("type", "image_url", "image_url", dataUrl),
                "include_blocks", false,
                "document_annotation_prompt", EXTRACTION_PROMPT,
                "document_annotation_format", Map.of("type", "json_object"));
        JsonNode response = client.post().uri("/v1/ocr")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve().body(JsonNode.class);
        if (response == null || !response.hasNonNull("document_annotation")) {
            throw new IllegalStateException("Mistral OCR returned no structured bill draft.");
        }
        try {
            JsonNode annotation = mapper.readTree(response.get("document_annotation").asText());
            return mapper.treeToValue(annotation, ScanExtraction.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Mistral OCR returned an invalid structured bill draft.", exception);
        }
    }
}
