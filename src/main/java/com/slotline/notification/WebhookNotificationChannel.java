package com.slotline.notification;

import com.slotline.config.SlotlineProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "slotline.notifications.webhook", name = "url")
public class WebhookNotificationChannel implements NotificationChannel {

    private final WebhookGateway gateway;
    private final String url;

    public WebhookNotificationChannel(WebhookGateway gateway, SlotlineProperties properties) {
        this.gateway = gateway;
        this.url = properties.getNotifications().getWebhook().getUrl();
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public void deliver(NotificationMessage message) {
        gateway.post(url, message);
    }
}
