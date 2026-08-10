package com.slotline.reservation.dto;

import com.slotline.reservation.Reservation;
import com.slotline.reservation.ReservationStatus;

import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long resourceId,
        Long userId,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        Instant createdAt) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getResource().getId(),
                reservation.getUserId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
