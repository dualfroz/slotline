package com.slotline.reservation;

import com.slotline.availability.AvailabilityService;
import com.slotline.common.InvalidReservationException;
import com.slotline.common.NotFoundException;
import com.slotline.common.SlotConflictException;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ReservationBooker {

    private final ReservationRepository reservations;
    private final ResourceRepository resources;
    private final AvailabilityService availability;

    public ReservationBooker(ReservationRepository reservations, ResourceRepository resources,
                             AvailabilityService availability) {
        this.reservations = reservations;
        this.resources = resources;
        this.availability = availability;
    }

    public Resource lock(Long resourceId) {
        return resources.findByIdForBooking(resourceId)
                .orElseThrow(() -> new NotFoundException("Resource " + resourceId + " not found"));
    }

    public Reservation insert(Long userId, Resource resource, Instant start, Instant end, String seriesId) {
        if (!start.isBefore(end)) {
            throw new InvalidReservationException("startTime must be strictly before endTime");
        }
        availability.verifyOpen(resource, start, end);

        List<Reservation> overlaps = reservations.findActiveOverlaps(resource.getId(), start, end);
        if (!overlaps.isEmpty()) {
            throw new SlotConflictException(
                    "Slot overlaps an active reservation on resource " + resource.getId());
        }

        availability.verifyBuffer(resource, start, end);

        Reservation reservation = new Reservation(resource, userId, start, end, Instant.now());
        if (seriesId != null) {
            reservation.assignSeries(seriesId);
        }
        reservations.save(reservation);
        return reservation;
    }
}
