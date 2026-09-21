package com.slotline.reservation;

import com.slotline.common.ForbiddenOperationException;
import com.slotline.common.InvalidReservationException;
import com.slotline.common.NotFoundException;
import com.slotline.notification.NotificationMessage;
import com.slotline.notification.NotificationService;
import com.slotline.notification.NotificationType;
import com.slotline.recurrence.Occurrence;
import com.slotline.recurrence.RecurrenceExpander;
import com.slotline.reservation.dto.RecurringReservationRequest;
import com.slotline.reservation.dto.RecurringReservationResponse;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.reservation.dto.ReservationResponse;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceRepository;
import com.slotline.security.AuthenticatedUser;
import com.slotline.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservations;
    private final ResourceRepository resources;
    private final ReservationBooker booker;
    private final NotificationService notifications;
    private final WaitlistService waitlist;
    private final RecurrenceExpander recurrenceExpander;

    public ReservationService(ReservationRepository reservations, ResourceRepository resources,
                              ReservationBooker booker, NotificationService notifications,
                              WaitlistService waitlist, RecurrenceExpander recurrenceExpander) {
        this.reservations = reservations;
        this.resources = resources;
        this.booker = booker;
        this.notifications = notifications;
        this.waitlist = waitlist;
        this.recurrenceExpander = recurrenceExpander;
    }

    @Transactional
    public ReservationResponse create(AuthenticatedUser actor, ReservationRequest request) {
        Resource resource = lockForBooking(actor, request.resourceId());
        Reservation reservation = booker.insert(actor.id(), resource, request.startTime(), request.endTime(), null);
        notify(NotificationType.RESERVATION_CREATED, reservation, "Your reservation is confirmed");
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public RecurringReservationResponse createSeries(AuthenticatedUser actor, RecurringReservationRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new InvalidReservationException("startTime must be strictly before endTime");
        }
        Resource resource = lockForBooking(actor, request.resourceId());
        List<Occurrence> occurrences = recurrenceExpander.expand(
                request.startTime(), request.endTime(), request.rrule(), ZoneId.of(resource.getZoneId()));
        String seriesId = UUID.randomUUID().toString();
        List<ReservationResponse> created = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            Reservation reservation = booker.insert(actor.id(), resource, occurrence.start(), occurrence.end(), seriesId);
            created.add(ReservationResponse.from(reservation));
        }
        notifySeries(actor.id(), resource, seriesId, created.size());
        return new RecurringReservationResponse(seriesId, created.size(), created);
    }

    @Transactional
    public ReservationResponse cancel(AuthenticatedUser actor, Long reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation " + reservationId + " not found"));
        ensureSameTenant(actor, reservation.getOrganizationId());
        boolean owner = reservation.getUserId().equals(actor.id());
        if (!owner && actor.role() != Role.ADMIN) {
            throw new ForbiddenOperationException("You may only cancel your own reservations");
        }
        reservation.cancel();
        notify(NotificationType.RESERVATION_CANCELLED, reservation, "Your reservation was cancelled");
        waitlist.promoteAfterCancellation(reservation);
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listForUser(Long userId) {
        return reservations.findByUserIdOrderByStartTimeDesc(userId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listActiveForResource(AuthenticatedUser actor, Long resourceId) {
        Resource resource = requireInTenant(actor, resourceId);
        return reservations.findByResourceIdAndStatusOrderByStartTimeAsc(resource.getId(), ReservationStatus.ACTIVE)
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    private Resource lockForBooking(AuthenticatedUser actor, Long resourceId) {
        Resource resource = booker.lock(resourceId);
        ensureSameTenant(actor, resource.getOrganizationId());
        return resource;
    }

    private Resource requireInTenant(AuthenticatedUser actor, Long resourceId) {
        return resources.findByIdAndOrganizationId(resourceId, actor.organizationId())
                .orElseThrow(() -> new NotFoundException("Resource " + resourceId + " not found"));
    }

    private void ensureSameTenant(AuthenticatedUser actor, Long organizationId) {
        if (!organizationId.equals(actor.organizationId())) {
            throw new NotFoundException("Resource not found");
        }
    }

    private void notify(NotificationType type, Reservation reservation, String summary) {
        Resource resource = reservation.getResource();
        notifications.dispatch(new NotificationMessage(
                type,
                reservation.getOrganizationId(),
                reservation.getUserId(),
                resource.getId(),
                resource.getName(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                summary));
    }

    private void notifySeries(Long userId, Resource resource, String seriesId, int count) {
        notifications.dispatch(new NotificationMessage(
                NotificationType.RESERVATION_CREATED,
                resource.getOrganizationId(),
                userId,
                resource.getId(),
                resource.getName(),
                null,
                null,
                "Recurring series " + seriesId + " booked with " + count + " reservations"));
    }
}
