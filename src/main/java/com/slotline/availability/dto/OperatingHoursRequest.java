package com.slotline.availability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OperatingHoursRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {
}
