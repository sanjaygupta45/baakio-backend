package com.baakio;

import com.baakio.dto.scan.StoredDocument;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.provider.NotificationProvider;
import com.baakio.service.DocumentStorage;
import com.baakio.service.auth.GoogleIdentityVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "baakio.auth.email.enabled=true",
        "baakio.auth.google.client-id=test-google-client-id",
        "baakio.auth.otp.expose-development-code=true",
        "baakio.auth.otp.resend-cooldown-seconds=0",
        "baakio.notifications.routing.email-provider=development"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(EmailAuthenticationFlowTest.StorageConfiguration.class)
class EmailAuthenticationFlowTest {
    @Autowired MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleIdentityVerifier googleVerifier;

    @TestConfiguration
    static class StorageConfiguration {
        @Bean
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                @Override public void put(String key, byte[] content, String contentType) {}
                @Override public StoredDocument get(String key) {
                    return new StoredDocument(new byte[0], "application/octet-stream");
                }
                @Override public void delete(String key) {}
            };
        }

        @Bean
        NotificationProvider developmentEmailProvider() {
            return new NotificationProvider() {
                @Override public String id() { return "development"; }
                @Override public Set<NotificationChannel> channels() { return Set.of(NotificationChannel.EMAIL); }
                @Override public void send(NotificationChannel channel, NotificationMessage message) {}
            };
        }
    }

    @Test
    void emailRegistrationCreatesSessionAndCanSignInAgain() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS account_identity (provider VARCHAR(20) NOT NULL, "
                + "subject VARCHAR(255) NOT NULL, account_holder_id CHAR(36) NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (provider, subject))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS email_otp_global_lock (id TINYINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS email_otp_request_lock (email VARCHAR(190) PRIMARY KEY)");

        String email = "auth-flow@example.com";
        JsonNode requested = requestCode(email, "REGISTER");
        String registered = "{\"email\":\"" + email + "\",\"challengeId\":\""
                + requested.get("challengeId").asText() + "\",\"otp\":\""
                + requested.get("developmentCode").asText()
                + "\",\"displayName\":\"Owner\",\"businessName\":\"Test Shop\"}";
        var registration = mvc.perform(post("/api/v1/auth/email/register")
                        .with(csrf()).contentType("application/json").content(registered))
                .andExpect(status().isOk()).andReturn();
        assertEquals(email, json.readTree(registration.getResponse().getContentAsString())
                .path("accountHolder").path("email").asText());
        var duplicateRegistration = mvc.perform(post("/api/v1/auth/email/request").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"purpose\":\"REGISTER\"}"))
                .andExpect(status().isConflict()).andReturn();
        assertEquals("EMAIL_ALREADY_REGISTERED", json.readTree(duplicateRegistration.getResponse().getContentAsString())
                .path("code").asText());
        var sessionCookie = registration.getResponse().getCookie("SESSION");
        assertNotNull(sessionCookie);
        mvc.perform(get("/api/v1/account-holder").cookie(sessionCookie))
                .andExpect(status().isOk());

        JsonNode loginCode = requestCode(email, "LOGIN");
        String login = "{\"email\":\"" + email + "\",\"challengeId\":\""
                + loginCode.get("challengeId").asText() + "\",\"otp\":\""
                + loginCode.get("developmentCode").asText() + "\"}";
        mvc.perform(post("/api/v1/auth/email/login").with(csrf())
                        .contentType("application/json").content(login))
                .andExpect(status().isOk());
    }

    @Test
    void googleRegistrationCreatesSessionAndExistingSubjectSignsInAgain() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS account_identity (provider VARCHAR(20) NOT NULL, "
                + "subject VARCHAR(255) NOT NULL, account_holder_id CHAR(36) NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (provider, subject))");
        when(googleVerifier.verify("valid-id-token")).thenReturn(
                new GoogleIdentityVerifier.VerifiedGoogleIdentity(
                        "google-flow-subject", "google-flow@example.com", "Google Owner"));

        mvc.perform(get("/api/v1/auth/config"))
                .andExpect(status().isOk());
        String registrationBody = "{\"idToken\":\"valid-id-token\",\"businessName\":\"Google Shop\"}";
        var registration = mvc.perform(post("/api/v1/auth/google").with(csrf())
                        .contentType("application/json").content(registrationBody))
                .andExpect(status().isOk()).andReturn();
        JsonNode registered = json.readTree(registration.getResponse().getContentAsString());
        assertEquals("google-flow@example.com", registered.path("accountHolder").path("email").asText());
        var sessionCookie = registration.getResponse().getCookie("SESSION");
        assertNotNull(sessionCookie);
        mvc.perform(get("/api/v1/account-holder").cookie(sessionCookie))
                .andExpect(status().isOk());

        var signIn = mvc.perform(post("/api/v1/auth/google").with(csrf())
                        .contentType("application/json").content("{\"idToken\":\"valid-id-token\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(registered.path("accountHolder").path("id").asText(),
                json.readTree(signIn.getResponse().getContentAsString()).path("accountHolder").path("id").asText());
    }

    private JsonNode requestCode(String email, String purpose) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}";
        var result = mvc.perform(post("/api/v1/auth/email/request").with(csrf())
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
