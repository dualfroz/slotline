package com.slotline.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RecurringReservationRequest(
        @NotNull Long resourceId,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotBlank String rrule) {
}
