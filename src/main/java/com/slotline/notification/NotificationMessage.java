package com.slotline.notification;

import java.time.Instant;

public record NotificationMessage(
        NotificationType type,
        Long organizationId,
        Long recipientUserId,
        Long resourceId,
        String resourceName,
        Instant startTime,
        Instant endTime,
        String summary) {
}
