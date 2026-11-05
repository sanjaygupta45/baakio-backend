package com.baakio.notification.email;

import com.baakio.notification.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class WelcomeEmailTemplate {
    private final String htmlTemplate;
    private final String frontendUrl;

    public WelcomeEmailTemplate(@Value("${baakio.frontend-url:https://www.baakio.shop}") String frontendUrl) {
        this.frontendUrl = trimTrailingSlash(frontendUrl);
        try (var input = new ClassPathResource("email/welcome.html").getInputStream()) {
            htmlTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Welcome email template is missing.", exception);
        }
    }

    public RenderedEmail render(NotificationMessage message) {
        if (!"welcome".equals(message.templateName()) || message.variables().size() != 2) {
            throw new IllegalArgumentException("A welcome email message is required.");
        }

        boolean hindi = "hi".equals(message.languageCode());
        String displayName = message.variables().get(0);
        String businessName = message.variables().get(1);
        WelcomeCopy copy = hindi ? WelcomeCopy.hindi(displayName, businessName) : WelcomeCopy.english(displayName, businessName);
        String appUrl = frontendUrl + "/app.html";
        String privacyUrl = frontendUrl + "/privacy.html";
        String termsUrl = frontendUrl + "/terms.html";
        String whatsappUrl = "https://wa.me/918738074517";
        String emailUrl = "mailto:baakio.shop@gmail.com";
        String xUrl = "https://x.com/Dev_Sanjay2004";

        String htmlText = htmlTemplate
                .replace("{{LANG}}", hindi ? "hi" : "en")
                .replace("{{PREHEADER}}", escape(copy.introduction()))
                .replace("{{HEADING}}", escape(copy.heading()))
                .replace("{{INTRODUCTION}}", escape(copy.introduction()))
                .replace("{{CTA}}", escape(copy.cta()))
                .replace("{{APP_URL}}", escape(appUrl))
                .replace("{{STEPS_HEADING}}", escape(copy.stepsHeading()))
                .replace("{{STEP1_TITLE}}", escape(copy.step1Title()))
                .replace("{{STEP1_BODY}}", escape(copy.step1Body()))
                .replace("{{STEP2_TITLE}}", escape(copy.step2Title()))
                .replace("{{STEP2_BODY}}", escape(copy.step2Body()))
                .replace("{{STEP3_TITLE}}", escape(copy.step3Title()))
                .replace("{{STEP3_BODY}}", escape(copy.step3Body()))
                .replace("{{NOTE_HEADING}}", escape(copy.noteHeading()))
                .replace("{{NOTE_BODY}}", escape(copy.noteBody()))
                .replace("{{CONTACT_HEADING}}", escape(copy.contactHeading()))
                .replace("{{CONTACT_BODY}}", escape(copy.contactBody()))
                .replace("{{WHATSAPP_URL}}", escape(whatsappUrl))
                .replace("{{EMAIL_URL}}", escape(emailUrl))
                .replace("{{X_URL}}", escape(xUrl))
                .replace("{{PRIVACY_URL}}", escape(privacyUrl))
                .replace("{{TERMS_URL}}", escape(termsUrl))
                .replace("{{FOOTER}}", escape(copy.footer()));

        String plainText = "Baakio\n\n" + copy.heading() + "\n" + copy.introduction() + "\n\n"
                + copy.stepsHeading() + "\n"
                + "1. " + copy.step1Title() + " — " + copy.step1Body() + "\n"
                + "2. " + copy.step2Title() + " — " + copy.step2Body() + "\n"
                + "3. " + copy.step3Title() + " — " + copy.step3Body() + "\n\n"
                + copy.noteHeading() + "\n" + copy.noteBody() + "\n\n"
                + copy.contactHeading() + "\n" + copy.contactBody() + "\n"
                + "WhatsApp: " + whatsappUrl + "\nEmail: baakio.shop@gmail.com\nX: " + xUrl + "\n\n"
                + "Open Baakio: " + appUrl + "\nPrivacy: " + privacyUrl + "\nTerms: " + termsUrl + "\n\n"
                + copy.footer();

        return new RenderedEmail(message.subject(), plainText, htmlText);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://www.baakio.shop";
        return value.replaceFirst("/+$", "");
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public record RenderedEmail(String subject, String plainText, String htmlText) {}

    private record WelcomeCopy(
            String heading,
            String introduction,
            String cta,
            String stepsHeading,
            String step1Title,
            String step1Body,
            String step2Title,
            String step2Body,
            String step3Title,
            String step3Body,
            String noteHeading,
            String noteBody,
            String contactHeading,
            String contactBody,
            String footer
    ) {
        static WelcomeCopy english(String displayName, String businessName) {
            return new WelcomeCopy(
                    "Welcome to Baakio, " + displayName,
                    "Your shop account for " + businessName
                            + " is ready. Let’s make every outstanding payment easier to follow.",
                    "Open your Baakio account",
                    "Start with these three simple steps",
                    "Add your customers", "Save names and existing balances.",
                    "Record sales and payments", "Keep every purchase and payment together.",
                    "Set a promise and follow up", "Know the next date and review reminders before sharing.",
                    "A quick note about Baakio",
                    "Baakio does not collect payments or send WhatsApp messages automatically. AI bill scans create drafts—you review and approve them before saving.",
                    "Need help?", "WhatsApp: 8738074517 · Email: baakio.shop@gmail.com",
                    "Every outstanding payment, made clear.");
        }

        static WelcomeCopy hindi(String displayName, String businessName) {
            return new WelcomeCopy(
                    "Baakio में आपका स्वागत है, " + displayName,
                    "आपकी दुकान " + businessName
                            + " का खाता तैयार है। अब ग्राहकों के बाकी और भुगतान का हिसाब साफ रखें।",
                    "अपना Baakio खाता खोलें",
                    "इन तीन आसान कदमों से शुरू करें",
                    "ग्राहक जोड़ें", "नाम और पहले से बाकी रकम सेव करें।",
                    "बिल और भुगतान दर्ज करें", "हर खरीद और भुगतान एक ही खाते में रखें।",
                    "भुगतान का वादा और फॉलो-अप रखें", "अगली तारीख देखें और भेजने से पहले रिमाइंडर जाँचें।",
                    "Baakio के बारे में एक जरूरी बात",
                    "Baakio भुगतान नहीं लेता और WhatsApp संदेश अपने आप नहीं भेजता। AI बिल स्कैन पहले ड्राफ्ट बनाता है—सेव करने से पहले आप जाँचकर मंज़ूर करते हैं।",
                    "मदद चाहिए?", "WhatsApp: 8738074517 · ईमेल: baakio.shop@gmail.com",
                    "हर बाकी का साफ़ हिसाब।");
        }
    }
}
