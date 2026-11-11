package com.baakio;

import com.baakio.ai.ScanExtraction;
import com.baakio.ai.ScanExtractionLine;
import com.baakio.ai.ScanExtractor;
import com.baakio.dto.scan.StoredDocument;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.provider.NotificationProvider;
import com.baakio.service.DocumentStorage;
import com.baakio.service.ScanJobWorker;
import com.baakio.service.auth.GoogleIdentityVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    @MockitoBean ScanExtractor scanExtractor;
    @Autowired ScanJobWorker scanWorker;

    @TestConfiguration
    static class StorageConfiguration {
        @Bean
        DocumentStorage documentStorage() {
            return new DocumentStorage() {
                private final ConcurrentHashMap<String, StoredDocument> documents = new ConcurrentHashMap<>();
                @Override public void put(String key, byte[] content, String contentType) {
                    documents.put(key, new StoredDocument(content.clone(), contentType));
                }
                @Override public StoredDocument get(String key) {
                    StoredDocument document = documents.get(key);
                    if (document == null) throw new IllegalStateException("Document not found.");
                    return document;
                }
                @Override public void delete(String key) { documents.remove(key); }
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
    void billUploadReviewAndConfirmationCreatePurchaseInCustomerAccount() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS account_identity (provider VARCHAR(20) NOT NULL, "
                + "subject VARCHAR(255) NOT NULL, account_holder_id CHAR(36) NOT NULL, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (provider, subject))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS email_otp_global_lock (id TINYINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS email_otp_request_lock (email VARCHAR(190) PRIMARY KEY)");

        String email = "bill-scan-flow@example.com";
        JsonNode code = requestCode(email, "REGISTER");
        String registrationBody = "{\"email\":\"" + email + "\",\"challengeId\":\""
                + code.get("challengeId").asText() + "\",\"otp\":\""
                + code.get("developmentCode").asText()
                + "\",\"displayName\":\"Scan Owner\",\"businessName\":\"Scan Test Shop\"}";
        var registration = mvc.perform(post("/api/v1/auth/email/register").with(csrf())
                        .contentType("application/json").content(registrationBody))
                .andExpect(status().isOk()).andReturn();
        var session = registration.getResponse().getCookie("SESSION");
        assertNotNull(session);

        var customerResult = mvc.perform(post("/api/v1/customers").with(csrf()).cookie(session)
                        .contentType("application/json")
                        .content("{\"displayName\":\"Scan Test Customer\",\"preferredMessageLocale\":\"en-IN\"}"))
                .andExpect(status().isOk()).andReturn();
        long customerId = json.readTree(customerResult.getResponse().getContentAsString()).path("id").asLong();
        jdbc.execute("ALTER TABLE outbox_event ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE scan_attempt ALTER COLUMN started_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE scan_draft ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE scan_draft ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE purchase ALTER COLUMN posted_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE audit_event ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbc.execute("ALTER TABLE idempotency_record ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");

        byte[] image = pngImage();
        var uploaded = mvc.perform(multipart("/api/v1/customers/" + customerId + "/scans")
                        .file(new MockMultipartFile("file", "bill.png", "image/png", image))
                        .param("keepAttachment", "true")
                        .header("Idempotency-Key", "bill-scan-upload-1")
                        .with(csrf()).cookie(session))
                .andExpect(status().isAccepted()).andReturn();
        long scanId = json.readTree(uploaded.getResponse().getContentAsString()).path("scanId").asLong();

        when(scanExtractor.extract(any(byte[].class), eq("image/png"))).thenReturn(
                new ScanExtraction(true, "2026-09-20", "Test bill", "0.00", "20.00",
                        java.util.List.of(new ScanExtractionLine("Flour", "2", "kg", "10.00", "20.00",
                                0.99, 0.99, 0.99))));
        scanWorker.process(scanId);

        var reviewResult = mvc.perform(get("/api/v1/scans/" + scanId).cookie(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode review = json.readTree(reviewResult.getResponse().getContentAsString());
        assertEquals("REVIEW_REQUIRED", review.path("status").asText());
        assertEquals("Flour", review.path("draft").path("lines").get(0).path("name").asText());
        assertEquals(1, review.path("draftVersion").asLong());

        mvc.perform(get("/api/v1/scans/" + scanId + "/image").cookie(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(image));

        String confirmation = "{\"version\":1,\"occurredOn\":\"2026-09-20\",\"description\":\"Test bill\","
                + "\"discount\":\"0.00\",\"lines\":[{\"name\":\"Flour\",\"quantity\":\"2\","
                + "\"unit\":\"kg\",\"unitPrice\":\"10.00\"}],\"paidNow\":null}";
        var purchaseResult = mvc.perform(post("/api/v1/scans/" + scanId + "/confirm").with(csrf())
                        .cookie(session).header("Idempotency-Key", "bill-scan-confirm-1")
                        .contentType("application/json").content(confirmation))
                .andExpect(status().isOk()).andReturn();
        JsonNode purchase = json.readTree(purchaseResult.getResponse().getContentAsString());
        assertEquals(customerId, purchase.path("customerId").asLong());
        assertEquals("20.00", purchase.path("total").asText());
        assertEquals("Flour", purchase.path("lines").get(0).path("name").asText());

        var accountPurchases = mvc.perform(get("/api/v1/customers/" + customerId + "/purchases").cookie(session))
                .andExpect(status().isOk()).andReturn();
        JsonNode purchaseHistory = json.readTree(accountPurchases.getResponse().getContentAsString());
        assertEquals(1, purchaseHistory.path("total").asInt());
        assertEquals("20.00", purchaseHistory.path("items").get(0).path("total").asText());

        var postedScan = mvc.perform(get("/api/v1/scans/" + scanId).cookie(session))
                .andExpect(status().isOk()).andReturn();
        assertEquals("POSTED", json.readTree(postedScan.getResponse().getContentAsString()).path("status").asText());
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

        var emailSignIn = mvc.perform(post("/api/v1/auth/email/request").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"google-flow@example.com\",\"purpose\":\"LOGIN\"}"))
                .andExpect(status().isConflict()).andReturn();
        assertEquals("EMAIL_SIGN_IN_NOT_LINKED", json.readTree(emailSignIn.getResponse().getContentAsString())
                .path("code").asText());

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

    private byte[] pngImage() throws IOException {
        var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
