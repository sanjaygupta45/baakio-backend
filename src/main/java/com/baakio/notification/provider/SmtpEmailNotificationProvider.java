package com.baakio.notification.provider;

import com.baakio.exception.NotificationDeliveryException;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.email.AuthenticationEmailTemplate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailNotificationProvider implements NotificationProvider {
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final AuthenticationEmailTemplate authTemplate;

    public SmtpEmailNotificationProvider(JavaMailSender mailSender,
            @Value("${baakio.notifications.email.from}") String fromAddress,
            AuthenticationEmailTemplate authTemplate) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.authTemplate = authTemplate;
    }

    @Override public String id() { return "smtp"; }
    @Override public Set<NotificationChannel> channels() { return Set.of(NotificationChannel.EMAIL); }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        if (fromAddress.isBlank()) throw new NotificationDeliveryException("The email sender address is not configured.");
        if (message.emailOtpPurpose() != null) {
            sendAuthenticationCode(message);
            return;
        }
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(message.recipient());
        email.setSubject(message.subject() == null ? "Baakio notification" : message.subject());
        email.setText(message.body());
        mailSender.send(email);
    }

    private void sendAuthenticationCode(NotificationMessage message) {
        AuthenticationEmailTemplate.RenderedEmail rendered = authTemplate.render(message);
        try {
            MimeMessage email = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(email, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, "Baakio");
            helper.setTo(message.recipient());
            helper.setSubject(rendered.subject());
            helper.setText(rendered.plainText(), rendered.htmlText());
            helper.addInline("baakio-logo", new ClassPathResource("email/baakio-logo.png"), "image/png");
            mailSender.send(email);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new NotificationDeliveryException("The authentication email could not be prepared.", exception);
        }
    }
}
