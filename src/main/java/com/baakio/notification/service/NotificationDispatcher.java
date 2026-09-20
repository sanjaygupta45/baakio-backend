package com.baakio.notification.service;

import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.exception.NotificationDeliveryException;
import com.baakio.notification.provider.NotificationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDispatcher {
    private final Map<NotificationChannel, String> providerIds = new EnumMap<>(NotificationChannel.class);
    private final Map<String, NotificationProvider> providers;

    public NotificationDispatcher(List<NotificationProvider> providers,
            @Value("${baakio.notifications.routing.sms-provider}") String smsProvider,
            @Value("${baakio.notifications.routing.whatsapp-provider}") String whatsappProvider,
            @Value("${baakio.notifications.routing.email-provider}") String emailProvider) {
        this.providers = providers.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                NotificationProvider::id, provider -> provider));
        providerIds.put(NotificationChannel.SMS, smsProvider);
        providerIds.put(NotificationChannel.WHATSAPP, whatsappProvider);
        providerIds.put(NotificationChannel.EMAIL, emailProvider);
    }

    public void send(NotificationChannel channel, NotificationMessage message) {
        String providerId = providerIds.get(channel);
        NotificationProvider provider = providers.get(providerId);
        if (provider == null || !provider.channels().contains(channel)) {
            throw new NotificationDeliveryException("No provider is configured for the " + channel + " channel.");
        }
        try {
            provider.send(channel, message);
        } catch (NotificationDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NotificationDeliveryException("The notification could not be delivered.", exception);
        }
    }
}
