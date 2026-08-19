package com.slotline.reservation.dto;

import java.util.List;

public record RecurringReservationResponse(String seriesId, int count, List<ReservationResponse> reservations) {
}
