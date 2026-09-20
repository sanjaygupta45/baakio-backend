package com.baakio.notification.provider;

import com.baakio.exception.NotificationDeliveryException;
import com.baakio.notification.fast2sms.Fast2SmsClient;
import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class Fast2SmsNotificationProvider implements NotificationProvider {
    private final Fast2SmsClient client;

    public Fast2SmsNotificationProvider(Fast2SmsClient client) { this.client = client; }

    @Override public String id() { return "fast2sms"; }
    @Override public Set<NotificationChannel> channels() { return Set.of(NotificationChannel.SMS, NotificationChannel.WHATSAPP); }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        if (channel == NotificationChannel.SMS) {
            if (message.type() == NotificationType.OTP) {
                client.sendOtp(message.recipient(), message.variables().getFirst(),
                        message.expiresInSeconds(), message.referenceId());
            } else {
                client.sendQuickSms(message.recipient(), message.body(), message.referenceId());
            }
            return;
        }
        if (channel == NotificationChannel.WHATSAPP) {
            client.sendWhatsAppTemplate(message);
            return;
        }
        throw new NotificationDeliveryException("Fast2SMS does not support the requested channel.");
    }
}
