package com.baakio.notification.email;

import com.baakio.enums.EmailOtpPurpose;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.provider.SmtpEmailNotificationProvider;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
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

class AuthenticationEmailTemplateTest {
    private final AuthenticationEmailTemplate template = new AuthenticationEmailTemplate();

    @Test
    void loginCodeHasReadableHtmlAndPlainText() {
        NotificationMessage message = NotificationMessage.emailOtp("owner@example.com", "123456",
                "en-IN", "request-1", 300, EmailOtpPurpose.LOGIN);

        var rendered = template.render(message);

        assertEquals("Your Baakio sign-in code", rendered.subject());
        assertTrue(rendered.plainText().contains("123456"));
        assertTrue(rendered.plainText().contains("5 minutes"));
        assertTrue(rendered.htmlText().contains("Your sign-in code"));
        assertTrue(rendered.htmlText().contains("123456"));
        assertTrue(rendered.htmlText().contains("Never share this code"));
        assertTrue(rendered.htmlText().contains("cid:baakio-logo"));
        assertFalse(rendered.htmlText().contains("{{"));
    }

    @Test
    void registrationAndLinkUseTheirOwnWordingAndHindiIsSupported() {
        var registration = template.render(NotificationMessage.emailOtp("owner@example.com", "123456",
                "hi-IN", "request-2", 300, EmailOtpPurpose.REGISTER));
        var link = template.render(NotificationMessage.emailOtp("owner@example.com", "123456",
                "en-IN", "request-3", 60, EmailOtpPurpose.LINK));

        assertTrue(registration.subject().contains("सत्यापन"));
        assertTrue(registration.htmlText().contains("5 मिनट"));
        assertEquals("Link your email to Baakio", link.subject());
        assertTrue(link.plainText().contains("1 minute"));
    }

    @Test
    void smtpProviderSendsMultipartAlternative() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        var provider = new SmtpEmailNotificationProvider(sender, "login@auth.baakio.shop", template);

        provider.send(NotificationChannel.EMAIL, NotificationMessage.emailOtp("owner@example.com",
                "654321", "en-IN", "request-4", 300, EmailOtpPurpose.LOGIN));

        verify(sender).send(mime);
        mime.saveChanges();
        assertEquals("Your Baakio sign-in code", mime.getSubject());
        assertTrue(mime.getFrom()[0].toString().contains("Baakio"));
        assertTrue(mime.getContentType().contains("multipart/"));
        String content = allText(mime);
        assertTrue(content.contains("654321"));
        assertTrue(content.contains("Your sign-in code"));
        assertTrue(content.contains("Never share this code"));
        assertTrue(content.contains("cid:baakio-logo"));
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
        if (part instanceof MimeBodyPart bodyPart && "<baakio-logo>".equals(bodyPart.getContentID())) {
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
