package com.baakio.notification.provider;

import com.baakio.exception.NotificationDeliveryException;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "spring.mail", name = "host")
public class SmtpEmailNotificationProvider implements NotificationProvider {
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailNotificationProvider(JavaMailSender mailSender,
            @Value("${baakio.notifications.email.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override public String id() { return "smtp"; }
    @Override public Set<NotificationChannel> channels() { return Set.of(NotificationChannel.EMAIL); }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        if (fromAddress.isBlank()) throw new NotificationDeliveryException("The email sender address is not configured.");
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(message.recipient());
        email.setSubject(message.subject() == null ? "Baakio notification" : message.subject());
        email.setText(message.body());
        mailSender.send(email);
    }
}
