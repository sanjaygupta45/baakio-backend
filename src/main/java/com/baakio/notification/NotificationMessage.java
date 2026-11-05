package com.baakio.notification;

import com.baakio.enums.EmailOtpPurpose;
import com.baakio.enums.NotificationType;

import java.util.List;

public record NotificationMessage(
        String recipient,
        NotificationType type,
        String subject,
        String body,
        String templateName,
        String languageCode,
        List<String> variables,
        Integer expiresInSeconds,
        String referenceId,
        EmailOtpPurpose emailOtpPurpose
) {
    public NotificationMessage {
        variables = variables == null ? List.of() : List.copyOf(variables);
    }

    public static NotificationMessage otp(String recipient, String code, String locale, String referenceId,
                                          int expiresInSeconds) {
        String languageCode = "hi-IN".equals(locale) ? "hi" : "en";
        String expiry = expiresInSeconds % 60 == 0
                ? expiresInSeconds / 60 + " minutes"
                : expiresInSeconds + " seconds";
        return new NotificationMessage(recipient, NotificationType.OTP,
                "Baakio verification code",
                "Your Baakio verification code is " + code + ". It expires in " + expiry + ".",
                null, languageCode, List.of(code), expiresInSeconds, referenceId, null);
    }

    public static NotificationMessage emailOtp(String recipient, String code, String locale,
                                               String referenceId, int expiresInSeconds,
                                               EmailOtpPurpose purpose) {
        String languageCode = "hi-IN".equals(locale) ? "hi" : "en";
        return new NotificationMessage(recipient, NotificationType.OTP,
                "Baakio verification code",
                "Your Baakio verification code is " + code + ".",
                null, languageCode, List.of(code), expiresInSeconds, referenceId, purpose);
    }

    public static NotificationMessage welcome(String recipient, String displayName,
                                              String businessName, String locale) {
        String languageCode = "hi-IN".equals(locale) ? "hi" : "en";
        String safeDisplayName = displayName == null ? "" : displayName;
        String safeBusinessName = businessName == null ? "" : businessName;
        return new NotificationMessage(recipient, NotificationType.TEMPLATE,
                languageCode.equals("hi")
                        ? "Baakio में आपका स्वागत है — आपकी दुकान तैयार है"
                        : "Welcome to Baakio — your shop is ready",
                languageCode.equals("hi")
                        ? "आपकी Baakio दुकान तैयार है।"
                        : "Your Baakio shop is ready.",
                "welcome", languageCode, List.of(safeDisplayName, safeBusinessName),
                null, null, null);
    }
}
