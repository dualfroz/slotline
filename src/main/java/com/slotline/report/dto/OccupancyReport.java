package com.slotline.report.dto;

import java.time.Instant;

public record OccupancyReport(
        Long resourceId,
        String resourceName,
        Instant from,
        Instant until,
        int reservationCount,
        long bookedMinutes,
        long windowMinutes,
        double utilizationPercent) {
}
