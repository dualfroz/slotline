package com.slotline.notification;

public interface WebhookGateway {

    void post(String url, NotificationMessage message);
}
