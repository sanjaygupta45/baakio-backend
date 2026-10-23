package com.baakio.notification.email;

import com.baakio.enums.EmailOtpPurpose;
import com.baakio.notification.NotificationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class AuthenticationEmailTemplate {
    private final String htmlTemplate;

    public AuthenticationEmailTemplate() {
        try (var input = new ClassPathResource("email/auth-otp.html").getInputStream()) {
            htmlTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Authentication email template is missing.", exception);
        }
    }

    public RenderedEmail render(NotificationMessage message) {
        EmailOtpPurpose purpose = message.emailOtpPurpose();
        if (purpose == null || message.variables().size() != 1 || message.expiresInSeconds() == null) {
            throw new IllegalArgumentException("An email OTP message is required.");
        }

        boolean hindi = "hi".equals(message.languageCode());
        String code = message.variables().getFirst();
        String duration = duration(message.expiresInSeconds(), hindi);
        String subject = subject(purpose, hindi);
        String heading = heading(purpose, hindi);
        String introduction = introduction(purpose, hindi);
        String codeLabel = hindi ? "एक बार इस्तेमाल होने वाला कोड" : "ONE-TIME CODE";
        String expiry = hindi ? "यह कोड " + duration + " में समाप्त हो जाएगा।"
                : "This code expires in " + duration + ".";
        String security = hindi
                ? "यह कोड किसी से साझा न करें। यदि आपने इसका अनुरोध नहीं किया, तो इस ईमेल को अनदेखा करें।"
                : "Never share this code. If you did not request it, you can ignore this email.";
        String footer = hindi ? "आपके कारोबार का साफ़ हिसाब" : "Your business ledger, made clear";
        String plainText = "Baakio\n\n" + heading + "\n" + introduction + "\n\n"
                + code + "\n\n" + expiry + "\n" + security + "\n";

        String htmlText = htmlTemplate
                .replace("{{LANG}}", hindi ? "hi" : "en")
                .replace("{{PREHEADER}}", escape(introduction + " " + expiry))
                .replace("{{HEADING}}", escape(heading))
                .replace("{{INTRODUCTION}}", escape(introduction))
                .replace("{{CODE_LABEL}}", escape(codeLabel))
                .replace("{{CODE}}", escape(code))
                .replace("{{EXPIRY}}", escape(expiry))
                .replace("{{SECURITY}}", escape(security))
                .replace("{{FOOTER}}", escape(footer));
        return new RenderedEmail(subject, plainText, htmlText);
    }

    private String duration(int seconds, boolean hindi) {
        if (seconds < 1) throw new IllegalArgumentException("OTP expiry must be positive.");
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            return hindi ? minutes + " मिनट" : minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return hindi ? seconds + " सेकंड" : seconds + (seconds == 1 ? " second" : " seconds");
    }

    private String subject(EmailOtpPurpose purpose, boolean hindi) {
        if (hindi) return switch (purpose) {
            case LOGIN -> "Baakio साइन-इन कोड";
            case REGISTER -> "Baakio ईमेल सत्यापन कोड";
            case LINK -> "Baakio ईमेल लिंक कोड";
        };
        return switch (purpose) {
            case LOGIN -> "Your Baakio sign-in code";
            case REGISTER -> "Verify your Baakio email";
            case LINK -> "Link your email to Baakio";
        };
    }

    private String heading(EmailOtpPurpose purpose, boolean hindi) {
        if (hindi) return switch (purpose) {
            case LOGIN -> "आपका साइन-इन कोड";
            case REGISTER -> "अपना ईमेल सत्यापित करें";
            case LINK -> "अपना ईमेल जोड़ें";
        };
        return switch (purpose) {
            case LOGIN -> "Your sign-in code";
            case REGISTER -> "Verify your email";
            case LINK -> "Link your email";
        };
    }

    private String introduction(EmailOtpPurpose purpose, boolean hindi) {
        if (hindi) return switch (purpose) {
            case LOGIN -> "Baakio में साइन इन करने के लिए यह कोड डालें।";
            case REGISTER -> "Baakio खाता बनाने के लिए यह कोड डालें।";
            case LINK -> "अपने Baakio खाते में यह ईमेल जोड़ने के लिए कोड डालें।";
        };
        return switch (purpose) {
            case LOGIN -> "Enter this code in Baakio to sign in.";
            case REGISTER -> "Enter this code in Baakio to create your account.";
            case LINK -> "Enter this code in Baakio to link this email to your account.";
        };
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record RenderedEmail(String subject, String plainText, String htmlText) {}
}
