package com.baakio.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {
    @Test
    void allowsBothProductionFrontendOrigins() {
        var source = new SecurityConfig().corsConfigurationSource(
                "https://baakio.shop", "https://www.baakio.shop");
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/config");

        assertEquals(List.of("https://baakio.shop", "https://www.baakio.shop"),
                source.getCorsConfiguration(request).getAllowedOrigins());
    }
}
