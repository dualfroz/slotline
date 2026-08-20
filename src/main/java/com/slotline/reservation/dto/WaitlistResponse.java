package com.slotline.reservation.dto;

import com.slotline.reservation.WaitlistEntry;
import com.slotline.reservation.WaitlistStatus;

import java.time.Instant;

public record WaitlistResponse(
        Long id,
        Long resourceId,
        Long userId,
        Instant startTime,
        Instant endTime,
        WaitlistStatus status,
        Long promotedReservationId,
        Instant createdAt) {

    public static WaitlistResponse from(WaitlistEntry entry) {
        return new WaitlistResponse(
                entry.getId(),
                entry.getResourceId(),
                entry.getUserId(),
                entry.getStartTime(),
                entry.getEndTime(),
                entry.getStatus(),
                entry.getPromotedReservationId(),
                entry.getCreatedAt());
    }
}
