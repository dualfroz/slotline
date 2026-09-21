package com.slotline.reservation;

import com.slotline.common.AvailabilityViolationException;
import com.slotline.common.InvalidReservationException;
import com.slotline.common.SlotConflictException;
import com.slotline.notification.NotificationMessage;
import com.slotline.notification.NotificationService;
import com.slotline.notification.NotificationType;
import com.slotline.reservation.dto.WaitlistRequest;
import com.slotline.reservation.dto.WaitlistResponse;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceService;
import com.slotline.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WaitlistService {

    private final WaitlistRepository waitlist;
    private final ReservationBooker booker;
    private final ResourceService resources;
    private final NotificationService notifications;

    public WaitlistService(WaitlistRepository waitlist, ReservationBooker booker, ResourceService resources,
                           NotificationService notifications) {
        this.waitlist = waitlist;
        this.booker = booker;
        this.resources = resources;
        this.notifications = notifications;
    }

    @Transactional
    public WaitlistResponse join(AuthenticatedUser actor, WaitlistRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new InvalidReservationException("startTime must be strictly before endTime");
        }
        Resource resource = resources.require(actor.organizationId(), request.resourceId());
        WaitlistEntry entry = new WaitlistEntry(resource.getId(), resource.getOrganizationId(), actor.id(),
                request.startTime(), request.endTime(), Instant.now());
        return WaitlistResponse.from(waitlist.save(entry));
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> listForUser(Long userId) {
        return waitlist.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(WaitlistResponse::from)
                .toList();
    }

    @Transactional
    public void promoteAfterCancellation(Reservation cancelled) {
        Resource resource = booker.lock(cancelled.getResource().getId());
        List<WaitlistEntry> candidates =
                waitlist.findWaitingOverlaps(resource.getId(), cancelled.getStartTime(), cancelled.getEndTime());
        for (WaitlistEntry candidate : candidates) {
            Reservation promoted;
            try {
                promoted = booker.insert(candidate.getUserId(), resource, candidate.getStartTime(),
                        candidate.getEndTime(), null);
            } catch (SlotConflictException | AvailabilityViolationException unavailable) {
                continue;
            }
            candidate.promote(promoted.getId());
            notifications.dispatch(new NotificationMessage(
                    NotificationType.WAITLIST_PROMOTED,
                    resource.getOrganizationId(),
                    candidate.getUserId(),
                    resource.getId(),
                    resource.getName(),
                    promoted.getStartTime(),
                    promoted.getEndTime(),
                    "A waitlisted slot became available and was booked for you"));
            return;
        }
    }
}
