package com.slotline.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    @Override
    public String name() {
        return "email";
    }

    @Override
    public void deliver(NotificationMessage message) {
        log.info("[email] user={} type={} resource='{}' [{} - {}] :: {}",
                message.recipientUserId(), message.type(), message.resourceName(),
                message.startTime(), message.endTime(), message.summary());
    }
}
