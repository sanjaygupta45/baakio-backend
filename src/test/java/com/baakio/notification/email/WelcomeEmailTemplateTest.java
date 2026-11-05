package com.baakio.notification.email;

import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.provider.SmtpEmailNotificationProvider;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WelcomeEmailTemplateTest {
    private final WelcomeEmailTemplate template = new WelcomeEmailTemplate("https://www.baakio.shop/");

    @Test
    void welcomeEmailExplainsTheFirstStepsAndProvidesSupportLinks() {
        var rendered = template.render(NotificationMessage.welcome(
                "owner@example.com", "Sanjay", "Sharma Hardware", "en-IN"));

        assertEquals("Welcome to Baakio — your shop is ready", rendered.subject());
        assertTrue(rendered.htmlText().contains("Welcome to Baakio, Sanjay"));
        assertTrue(rendered.htmlText().contains("Sharma Hardware"));
        assertTrue(rendered.htmlText().contains("https://www.baakio.shop/app.html"));
        assertTrue(rendered.htmlText().contains("8738074517"));
        assertTrue(rendered.htmlText().contains("baakio.shop@gmail.com"));
        assertTrue(rendered.htmlText().contains("https://x.com/Dev_Sanjay2004"));
        assertTrue(rendered.htmlText().contains("https://www.baakio.shop/privacy.html"));
        assertTrue(rendered.plainText().contains("Record sales and payments"));
        assertFalse(rendered.htmlText().contains("{{"));
    }

    @Test
    void hindiWelcomeEmailUsesHindiCopy() {
        var rendered = template.render(NotificationMessage.welcome(
                "owner@example.com", "संजय", "शर्मा हार्डवेयर", "hi-IN"));

        assertEquals("Baakio में आपका स्वागत है — आपकी दुकान तैयार है", rendered.subject());
        assertTrue(rendered.htmlText().contains("इन तीन आसान कदमों से शुरू करें"));
        assertTrue(rendered.htmlText().contains("मदद चाहिए?"));
    }

    @Test
    void smtpProviderSendsWelcomeEmailAsMultipartHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        var provider = new SmtpEmailNotificationProvider(sender, "hello@baakio.shop",
                new AuthenticationEmailTemplate(), template);

        provider.send(NotificationChannel.EMAIL, NotificationMessage.welcome(
                "owner@example.com", "Sanjay", "Sharma Hardware", "en-IN"));

        verify(sender).send(mime);
        mime.saveChanges();
        assertEquals("Welcome to Baakio — your shop is ready", mime.getSubject());
        assertTrue(mime.getContentType().contains("multipart/"));
        String content = allText(mime);
        assertTrue(content.contains("Open your Baakio account"));
        assertTrue(content.contains("8738074517"));
        assertTrue(content.contains("https://www.baakio.shop/app.html"));
        assertTrue(hasInlineLogo(mime));
    }

    private String allText(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                text.append(allText(multipart.getBodyPart(index)));
            }
            return text.toString();
        }
        return content instanceof String text ? text : "";
    }

    private boolean hasInlineLogo(Part part) throws Exception {
        if (part instanceof jakarta.mail.internet.MimeBodyPart bodyPart
                && "<baakio-logo>".equals(bodyPart.getContentID())) {
            return bodyPart.isMimeType("image/png");
        }
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int index = 0; index < multipart.getCount(); index++) {
                if (hasInlineLogo(multipart.getBodyPart(index))) return true;
            }
        }
        return false;
    }
}
