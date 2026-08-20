package com.slotline.notification;

public interface NotificationChannel {

    String name();

    void deliver(NotificationMessage message);
}
