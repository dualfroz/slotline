package com.slotline.availability.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.slotline.availability.OperatingHours;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OperatingHoursResponse(
        Long id,
        DayOfWeek dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime openTime,
        @JsonFormat(pattern = "HH:mm") LocalTime closeTime) {

    public static OperatingHoursResponse from(OperatingHours hours) {
        return new OperatingHoursResponse(hours.getId(), hours.getDayOfWeek(), hours.getOpenTime(),
                hours.getCloseTime());
    }
}
