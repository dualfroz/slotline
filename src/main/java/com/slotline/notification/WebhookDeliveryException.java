package com.slotline.notification;

public class WebhookDeliveryException extends RuntimeException {

    public WebhookDeliveryException(String url, Throwable cause) {
        super("Failed to deliver webhook to " + url, cause);
    }
}
