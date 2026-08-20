package com.slotline.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<NotificationChannel> channels;

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public void dispatch(NotificationMessage message) {
        for (NotificationChannel channel : channels) {
            try {
                channel.deliver(message);
            } catch (RuntimeException failure) {
                log.warn("Notification channel '{}' failed to deliver {} for user {}",
                        channel.name(), message.type(), message.recipientUserId(), failure);
            }
        }
    }
}
