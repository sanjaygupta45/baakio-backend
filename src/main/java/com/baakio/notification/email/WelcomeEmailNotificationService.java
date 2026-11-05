package com.baakio.notification.email;

import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import com.baakio.notification.service.NotificationDispatcher;
import com.baakio.service.auth.ShopCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WelcomeEmailNotificationService {
    private static final Logger log = LoggerFactory.getLogger(WelcomeEmailNotificationService.class);

    private final NotificationDispatcher notifications;

    public WelcomeEmailNotificationService(NotificationDispatcher notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWelcomeEmail(ShopCreatedEvent event) {
        if (event.email() == null || event.email().isBlank()) return;
        try {
            notifications.send(NotificationChannel.EMAIL,
                    NotificationMessage.welcome(event.email(), event.displayName(), event.businessName(), event.locale()));
        } catch (RuntimeException exception) {
            log.warn("Welcome email could not be delivered for account {}", event.accountHolderId(), exception);
        }
    }
}
