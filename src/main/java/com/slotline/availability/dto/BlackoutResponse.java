package com.slotline.availability.dto;

import com.slotline.availability.BlackoutDate;

import java.time.LocalDate;

public record BlackoutResponse(Long id, LocalDate startDate, LocalDate endDate, String reason) {

    public static BlackoutResponse from(BlackoutDate blackout) {
        return new BlackoutResponse(blackout.getId(), blackout.getStartDate(), blackout.getEndDate(),
                blackout.getReason());
    }
}
