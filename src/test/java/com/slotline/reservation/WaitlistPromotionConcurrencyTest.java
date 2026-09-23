package com.slotline.reservation;

import com.slotline.notification.NotificationChannel;
import com.slotline.notification.NotificationMessage;
import com.slotline.notification.NotificationType;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.reservation.dto.ReservationResponse;
import com.slotline.reservation.dto.WaitlistRequest;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WaitlistPromotionConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HoldingNotificationChannel holding;

    @Test
    void promotionWaitsForTheResourceLockAndCannotDoubleBookAConcurrentReservation() throws Exception {
        Organization organization = organizationRepository.save(
                new Organization("Promotion Race Org", "promotion-race-org", Instant.now()));
        Long courtId = resourceService.create(organization.getId(),
                new ResourceRequest("Court 2", "Indoor court")).id();
        AuthenticatedUser holder = actor(organization, "promotion-holder");
        AuthenticatedUser waiter = actor(organization, "promotion-waiter");
        AuthenticatedUser booker = actor(organization, "promotion-booker");

        Instant nine = Instant.parse("2026-09-02T09:00:00Z");
        Instant ten = Instant.parse("2026-09-02T10:00:00Z");
        Instant eleven = Instant.parse("2026-09-02T11:00:00Z");
        ReservationResponse held = reservationService.create(holder, new ReservationRequest(courtId, nine, ten));
        waitlistService.join(waiter, new WaitlistRequest(courtId, nine, eleven));

        holding.holdAfterBooking(booker.id());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationResponse> booking =
                    pool.submit(() -> reservationService.create(booker, new ReservationRequest(courtId, ten, eleven)));
            assertThat(holding.awaitHeld(20, TimeUnit.SECONDS)).isTrue();

            Future<ReservationResponse> cancellation = pool.submit(() -> reservationService.cancel(holder, held.id()));
            assertThatThrownBy(() -> cancellation.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            holding.release();
            ReservationResponse booked = booking.get(20, TimeUnit.SECONDS);
            cancellation.get(20, TimeUnit.SECONDS);

            assertThat(reservationRepository.findByResourceIdAndStatusOrderByStartTimeAsc(
                    courtId, ReservationStatus.ACTIVE))
                    .singleElement()
                    .satisfies(reservation -> assertThat(reservation.getId()).isEqualTo(booked.id()));
            assertThat(waitlistService.listForUser(waiter.id())).singleElement()
                    .satisfies(entry -> assertThat(entry.status()).isEqualTo(WaitlistStatus.WAITING));
        } finally {
            holding.release();
            pool.shutdownNow();
        }
    }

    private AuthenticatedUser actor(Organization organization, String username) {
        User user = userRepository.save(new User(organization.getId(), username,
                passwordEncoder.encode("password123"), Role.USER, Instant.now()));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), organization.getId());
    }

    @TestConfiguration
    static class HoldingConfig {

        @Bean
        HoldingNotificationChannel holdingNotificationChannel() {
            return new HoldingNotificationChannel();
        }
    }

    static class HoldingNotificationChannel implements NotificationChannel {

        private final CountDownLatch held = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile Long heldUserId;

        @Override
        public String name() {
            return "holding";
        }

        @Override
        public void deliver(NotificationMessage message) {
            if (message.type() != NotificationType.RESERVATION_CREATED
                    || !message.recipientUserId().equals(heldUserId)) {
                return;
            }
            held.countDown();
            try {
                released.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        void holdAfterBooking(Long userId) {
            heldUserId = userId;
        }

        boolean awaitHeld(long timeout, TimeUnit unit) throws InterruptedException {
            return held.await(timeout, unit);
        }

        void release() {
            released.countDown();
        }
    }
}
