package com.baakio.notification.provider;

import com.baakio.enums.NotificationChannel;
import com.baakio.notification.NotificationMessage;

import java.util.Set;

public interface NotificationProvider {
    String id();
    Set<NotificationChannel> channels();
    void send(NotificationChannel channel, NotificationMessage message);
}
