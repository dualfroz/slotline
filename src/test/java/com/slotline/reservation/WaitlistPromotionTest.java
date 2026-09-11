package com.slotline.reservation;

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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistPromotionTest {

    private static final Instant SLOT_START = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant SLOT_END = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private OrganizationRepository organizations;

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
