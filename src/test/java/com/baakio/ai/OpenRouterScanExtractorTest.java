package com.baakio.ai;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenRouterScanExtractorTest {
    private static final String BASE_URL = "https://openrouter.ai/api/v1";

    @Test
    void sendsBillImageToOpenRouterAndMapsStructuredResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenRouterScanExtractor extractor = extractor(builder, "test-key");
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("qwen/qwen3-vl-8b-instruct"),
                        org.hamcrest.Matchers.containsString("data:image/png;base64,aW1hZ2U="),
                        org.hamcrest.Matchers.containsString("\"response_format\":{\"type\":\"json_object\"}"))))
                .andRespond(withSuccess("""
                        {"choices":[{"message":{"content":"{\\"is_new_purchase\\":true,\\"occurred_on\\":\\"2026-11-11\\",\\"description\\":\\"Groceries\\",\\"discount\\":null,\\"written_total\\":\\"125.00\\",\\"items\\":[]}"}}]}
                        """, MediaType.APPLICATION_JSON));

        ScanExtraction extraction = extractor.extract("image".getBytes(), "image/png");

        assertTrue(extraction.isNewPurchase());
        assertEquals("2026-11-11", extraction.occurredOn());
        assertEquals("125.00", extraction.writtenTotal());
        server.verify();
    }

    @Test
    void failsClearlyWhenOpenRouterKeyIsMissing() {
        OpenRouterScanExtractor extractor = extractor(RestClient.builder(), " ");

        assertThrows(IllegalStateException.class, () -> extractor.extract(new byte[0], "image/png"));
    }

    private OpenRouterScanExtractor extractor(RestClient.Builder builder, String apiKey) {
        return new OpenRouterScanExtractor(builder, JsonMapper.builder().findAndAddModules().build(),
                BASE_URL, apiKey, "qwen/qwen3-vl-8b-instruct");
    }
}
