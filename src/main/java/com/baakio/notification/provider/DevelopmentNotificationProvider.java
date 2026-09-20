package com.baakio.notification.provider;

import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Profile("dev")
public class DevelopmentNotificationProvider implements NotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentNotificationProvider.class);

    @Override public String id() { return "development"; }
    @Override public Set<NotificationChannel> channels() { return Set.of(NotificationChannel.values()); }

    @Override
    public void send(NotificationChannel channel, NotificationMessage message) {
        log.info("Development notification accepted: channel={}, type={}, recipient={}, reference={}",
                channel, message.type(), mask(message.recipient()), message.referenceId());
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) return "***";
        return "***" + value.substring(value.length() - 4);
    }
}
