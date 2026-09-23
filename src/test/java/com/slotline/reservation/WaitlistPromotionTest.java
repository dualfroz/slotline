package com.slotline.reservation;

import com.slotline.availability.AvailabilityService;
import com.slotline.availability.dto.BlackoutRequest;
import com.slotline.availability.dto.OperatingHoursRequest;
import com.slotline.notification.NotificationChannel;
import com.slotline.notification.NotificationMessage;
import com.slotline.notification.NotificationType;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.ReservationResponse;
import com.slotline.reservation.dto.WaitlistRequest;
import com.slotline.reservation.dto.WaitlistResponse;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.resource.ResourceService;
import com.slotline.resource.dto.ResourceRequest;
import com.slotline.security.AuthenticatedUser;
import com.slotline.user.Role;
import com.slotline.user.User;
import com.slotline.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistPromotionTest {

    private static final Instant SLOT_START = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant SLOT_END = Instant.parse("2026-10-01T10:00:00Z");
    private static final ZoneId zone = ZoneId.of("Europe/Warsaw");
    private static final LocalDate monday = LocalDate.of(2026, 9, 7);

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private RecordingNotificationChannel recording;

    @Test
    void cancellingPromotesTheOldestWaiterAndNotifies() {
        Organization organization = organizations.save(new Organization("waitlist-org", "waitlist-org", Instant.now()));
        AuthenticatedUser holder = user(organization.getId(), "holder");
        AuthenticatedUser waiter = user(organization.getId(), "waiter");
        Long resourceId = resourceService.create(organization.getId(),
                new ResourceRequest("Popular court", "single slot")).id();

        ReservationResponse held = reservationService.create(holder,
                new ReservationRequest(resourceId, SLOT_START, SLOT_END));
        waitlistService.join(waiter, new WaitlistRequest(resourceId, SLOT_START, SLOT_END));

        recording.clear();
        reservationService.cancel(holder, held.id());

        assertThat(recording.byType(NotificationType.WAITLIST_PROMOTED))
                .anySatisfy(message -> assertThat(message.recipientUserId()).isEqualTo(waiter.id()));

        List<ReservationResponse> waiterReservations = reservationService.listForUser(waiter.id());
        assertThat(waiterReservations)
                .anySatisfy(reservation -> {
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
                    assertThat(reservation.startTime()).isEqualTo(SLOT_START);
                });

        List<WaitlistResponse> waiterEntries = waitlistService.listForUser(waiter.id());
        assertThat(waiterEntries).singleElement()
                .satisfies(entry -> assertThat(entry.status()).isEqualTo(WaitlistStatus.PROMOTED));
    }

    @Test
    void promotionSkipsWaiterOutsideOperatingHoursAndPromotesTheNextOne() {
        Organization organization = organizations.save(new Organization("waitlist-hours", "waitlist-hours", Instant.now()));
        AuthenticatedUser holder = user(organization.getId(), "hours-holder");
        AuthenticatedUser lateWaiter = user(organization.getId(), "hours-late-waiter");
        AuthenticatedUser fittingWaiter = user(organization.getId(), "hours-fitting-waiter");
        Long resourceId = resourceService.create(organization.getId(),
                new ResourceRequest("Morning studio", "open 9-11", zone.getId(), 0)).id();
        availabilityService.replaceOperatingHours(organization.getId(), resourceId,
                List.of(new OperatingHoursRequest(monday.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(11, 0))));

        ReservationResponse held = reservationService.create(holder,
                new ReservationRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0)));
        waitlistService.join(lateWaiter, new WaitlistRequest(resourceId, at(monday, 10, 0), at(monday, 12, 0)));
        waitlistService.join(fittingWaiter, new WaitlistRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0)));

        recording.clear();
        reservationService.cancel(holder, held.id());

        assertThat(waitlistService.listForUser(lateWaiter.id())).singleElement()
                .satisfies(entry -> assertThat(entry.status()).isEqualTo(WaitlistStatus.WAITING));
        assertThat(reservationService.listForUser(lateWaiter.id())).isEmpty();

        assertThat(waitlistService.listForUser(fittingWaiter.id())).singleElement()
                .satisfies(entry -> assertThat(entry.status()).isEqualTo(WaitlistStatus.PROMOTED));
        assertThat(reservationService.listForUser(fittingWaiter.id())).singleElement()
                .satisfies(reservation -> {
                    assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
                    assertThat(reservation.endTime()).isEqualTo(at(monday, 11, 0));
                });
        assertThat(recording.byType(NotificationType.WAITLIST_PROMOTED)).singleElement()
                .satisfies(message -> assertThat(message.recipientUserId()).isEqualTo(fittingWaiter.id()));
    }

    @Test
    void promotionLeavesWaiterWaitingWhenTheDayIsBlackedOut() {
        Organization organization = organizations.save(
                new Organization("waitlist-blackout", "waitlist-blackout", Instant.now()));
        AuthenticatedUser holder = user(organization.getId(), "blackout-holder");
        AuthenticatedUser waiter = user(organization.getId(), "blackout-waiter");
        Long resourceId = resourceService.create(organization.getId(),
                new ResourceRequest("Hall", "closed for maintenance later", zone.getId(), 0)).id();

        ReservationResponse held = reservationService.create(holder,
                new ReservationRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0)));
        waitlistService.join(waiter, new WaitlistRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0)));
        availabilityService.addBlackout(organization.getId(), resourceId,
                new BlackoutRequest(monday, monday, "maintenance"));

        recording.clear();
        reservationService.cancel(holder, held.id());

        assertThat(recording.byType(NotificationType.WAITLIST_PROMOTED)).isEmpty();
        assertThat(reservationService.listForUser(waiter.id())).isEmpty();
        assertThat(waitlistService.listForUser(waiter.id())).singleElement()
                .satisfies(entry -> assertThat(entry.status()).isEqualTo(WaitlistStatus.WAITING));
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant();
    }

    private AuthenticatedUser user(Long organizationId, String username) {
        User user = users.save(new User(organizationId, username, passwordEncoder.encode("password123"),
                Role.USER, Instant.now()));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), organizationId);
    }

    @TestConfiguration
    static class RecordingConfig {

        @Bean
        RecordingNotificationChannel recordingNotificationChannel() {
            return new RecordingNotificationChannel();
        }
    }

    static class RecordingNotificationChannel implements NotificationChannel {

        private final List<NotificationMessage> received = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "recording";
        }

        @Override
        public void deliver(NotificationMessage message) {
            received.add(message);
        }

        void clear() {
            received.clear();
        }

        List<NotificationMessage> byType(NotificationType type) {
            return received.stream().filter(message -> message.type() == type).toList();
        }
    }
}
