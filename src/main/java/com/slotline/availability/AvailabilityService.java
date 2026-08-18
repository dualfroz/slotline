package com.slotline.availability;

import com.slotline.availability.dto.BlackoutRequest;
import com.slotline.availability.dto.OperatingHoursRequest;
import com.slotline.common.AvailabilityViolationException;
import com.slotline.common.InvalidReservationException;
import com.slotline.reservation.Reservation;
import com.slotline.reservation.ReservationRepository;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class AvailabilityService {

    private final OperatingHoursRepository operatingHours;
    private final BlackoutDateRepository blackouts;
    private final ReservationRepository reservations;
    private final ResourceService resources;

    public AvailabilityService(OperatingHoursRepository operatingHours, BlackoutDateRepository blackouts,
                               ReservationRepository reservations, ResourceService resources) {
        this.operatingHours = operatingHours;
        this.blackouts = blackouts;
        this.reservations = reservations;
        this.resources = resources;
    }

    public void verifyOpen(Resource resource, Instant start, Instant end) {
        ZoneId zone = ZoneId.of(resource.getZoneId());
        ZonedDateTime zonedStart = start.atZone(zone);
        ZonedDateTime zonedEnd = end.atZone(zone);

        verifyOperatingHours(resource, zonedStart, zonedEnd);
        verifyNotBlackedOut(resource, zonedStart.toLocalDate(), zonedEnd.toLocalDate());
    }

    public void verifyBuffer(Resource resource, Instant start, Instant end) {
        int bufferMinutes = resource.getBufferMinutes();
        if (bufferMinutes <= 0) {
            return;
        }
        Duration buffer = Duration.ofMinutes(bufferMinutes);
        List<Reservation> neighbours =
                reservations.findActiveWithinWindow(resource.getId(), start.minus(buffer), end.plus(buffer));
        if (!neighbours.isEmpty()) {
            throw new AvailabilityViolationException(
                    "Requested slot violates the " + bufferMinutes + " minute buffer around an existing reservation");
        }
    }

    @Transactional
    public List<OperatingHours> replaceOperatingHours(Long organizationId, Long resourceId,
                                                      List<OperatingHoursRequest> windows) {
        Resource resource = resources.require(organizationId, resourceId);
        for (OperatingHoursRequest window : windows) {
            if (!window.openTime().isBefore(window.closeTime())) {
                throw new InvalidReservationException("openTime must be before closeTime");
            }
        }
        operatingHours.deleteByResourceId(resource.getId());
        List<OperatingHours> saved = windows.stream()
                .map(window -> new OperatingHours(resource.getId(), window.dayOfWeek(), window.openTime(),
                        window.closeTime()))
                .map(operatingHours::save)
                .toList();
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OperatingHours> listOperatingHours(Long organizationId, Long resourceId) {
        Resource resource = resources.require(organizationId, resourceId);
        return operatingHours.findByResourceId(resource.getId());
    }

    @Transactional
    public BlackoutDate addBlackout(Long organizationId, Long resourceId, BlackoutRequest request) {
        Resource resource = resources.require(organizationId, resourceId);
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidReservationException("endDate must not be before startDate");
        }
        return blackouts.save(new BlackoutDate(resource.getId(), request.startDate(), request.endDate(),
                request.reason()));
    }

    @Transactional(readOnly = true)
    public List<BlackoutDate> listBlackouts(Long organizationId, Long resourceId) {
        Resource resource = resources.require(organizationId, resourceId);
        return blackouts.findByResourceIdOrderByStartDateAsc(resource.getId());
    }

    @Transactional
    public void removeBlackout(Long organizationId, Long resourceId, Long blackoutId) {
        Resource resource = resources.require(organizationId, resourceId);
        blackouts.findById(blackoutId)
                .filter(blackout -> blackout.getResourceId().equals(resource.getId()))
                .ifPresent(blackouts::delete);
    }

    private void verifyOperatingHours(Resource resource, ZonedDateTime zonedStart, ZonedDateTime zonedEnd) {
        List<OperatingHours> resourceHours = operatingHours.findByResourceId(resource.getId());
        if (resourceHours.isEmpty()) {
            return;
        }
        if (!zonedStart.toLocalDate().equals(zonedEnd.toLocalDate())) {
            throw new AvailabilityViolationException(
                    "Reservation must fall within a single day of the resource operating hours");
        }
        LocalTime openAt = zonedStart.toLocalTime();
        LocalTime closeAt = zonedEnd.toLocalTime();
        boolean withinAnyWindow = resourceHours.stream()
                .filter(window -> window.getDayOfWeek() == zonedStart.getDayOfWeek())
                .anyMatch(window -> !openAt.isBefore(window.getOpenTime()) && !closeAt.isAfter(window.getCloseTime()));
        if (!withinAnyWindow) {
            throw new AvailabilityViolationException(
                    "Requested slot is outside the operating hours of resource " + resource.getId());
        }
    }

    private void verifyNotBlackedOut(Resource resource, LocalDate from, LocalDate until) {
        List<BlackoutDate> overlapping = blackouts.findOverlapping(resource.getId(), from, until);
        if (!overlapping.isEmpty()) {
            throw new AvailabilityViolationException(
                    "Resource " + resource.getId() + " is blacked out for the requested date");
        }
    }
}
