package com.baakio.notification.fast2sms;

import com.baakio.notification.NotificationMessage;
import com.baakio.enums.NotificationType;
import com.baakio.exception.NotificationDeliveryException;
import com.baakio.utils.IndianPhoneNumbers;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Fast2SmsClient {
    private final RestClient restClient;
    private final Fast2SmsProperties properties;

    public Fast2SmsClient(@Qualifier("fast2SmsRestClient") RestClient restClient, Fast2SmsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public void sendOtp(String phoneE164, String code, int expiresInSeconds, String referenceId) {
        requireConfigured(properties.getOtpTemplateId(), "OTP template ID");
        int expiryMinutes = Math.max(1, (expiresInSeconds + 59) / 60);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mobile", IndianPhoneNumbers.fast2SmsNumber(phoneE164));
        body.put("otp_id", properties.getOtpTemplateId());
        body.put("otp_expiry", expiryMinutes);
        body.put("otp_length", code.length());
        body.put("otp", code);
        body.put("udf1", referenceId);
        String variableTemplate = properties.getOtpVariablesTemplate();
        if (variableTemplate != null && !variableTemplate.isBlank()) {
            body.put("variables_values", variableTemplate.replace("{otp}", code));
        }
        post("/dev/otp/send", body);
    }

    public void sendQuickSms(String phoneE164, String message, String referenceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("route", properties.getQuickSmsRoute());
        body.put("numbers", IndianPhoneNumbers.fast2SmsNumber(phoneE164));
        body.put("message", message);
        body.put("udf1", referenceId);
        post("/dev/bulkV2", body);
    }

    public void sendWhatsAppTemplate(NotificationMessage message) {
        requireConfigured(properties.getWhatsappPhoneNumberId(), "WhatsApp phone number ID");
        String templateName = message.type() == NotificationType.OTP
                ? properties.getWhatsappOtpTemplateName() : message.templateName();
        requireConfigured(templateName, "WhatsApp template name");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", message.recipient().replace("+", ""));
        body.put("type", "template");
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", message.languageCode() == null ? "en" : message.languageCode()));
        List<String> variables = message.variables();
        List<Map<String, Object>> components = new ArrayList<>();
        if (!variables.isEmpty()) {
            List<Map<String, String>> parameters = variables.stream()
                    .map(value -> Map.of("type", "text", "text", value)).toList();
            components.add(Map.of("type", "body", "parameters", parameters));
        }
        if (message.type() == NotificationType.OTP && !properties.getWhatsappOtpButtonIndex().isBlank()) {
            components.add(Map.of("type", "button", "sub_type", "url",
                    "index", properties.getWhatsappOtpButtonIndex(), "parameters",
                    List.of(Map.of("type", "text", "text", variables.getFirst()))));
        }
        if (!components.isEmpty()) template.put("components", components);
        body.put("template", template);
        post("/dev/whatsapp/{version}/{phoneNumberId}/messages", body,
                properties.getWhatsappVersion(), properties.getWhatsappPhoneNumberId());
    }

    private void post(String path, Object payload, Object... uriVariables) {
        requireConfigured(properties.getApiKey(), "API key");
        try {
            Map<?, ?> response = restClient.post()
                    .uri(path, uriVariables)
                    .header("Authorization", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            if (response != null && Boolean.FALSE.equals(response.get("return"))) {
                throw new NotificationDeliveryException("Fast2SMS rejected the notification request.");
            }
        } catch (RestClientResponseException exception) {
            throw new NotificationDeliveryException("Fast2SMS returned HTTP " + exception.getStatusCode().value() + ".", exception);
        }
    }

    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new NotificationDeliveryException("Fast2SMS " + name + " is not configured.");
        }
    }
}
