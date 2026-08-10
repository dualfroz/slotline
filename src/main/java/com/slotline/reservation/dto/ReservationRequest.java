package com.slotline.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ReservationRequest(
        @NotNull Long resourceId,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
