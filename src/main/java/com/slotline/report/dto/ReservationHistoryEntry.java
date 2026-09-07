package com.slotline.report.dto;

import com.slotline.reservation.Reservation;
import com.slotline.reservation.ReservationStatus;

import java.time.Instant;

public record ReservationHistoryEntry(
        Long id,
        Long userId,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        String seriesId,
        Instant createdAt) {

    public static ReservationHistoryEntry from(Reservation reservation) {
        return new ReservationHistoryEntry(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getStatus(),
                reservation.getSeriesId(),
                reservation.getCreatedAt());
    }
}
