package com.slotline.reservation;

import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceRepository;
import com.slotline.security.AuthenticatedUser;
import com.slotline.user.Role;
import com.slotline.user.User;
import com.slotline.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.slotline.common.SlotConflictException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void onlyOneBookingWinsForTheSameSlotUnderConcurrency() throws Exception {
        Organization organization = organizationRepository.save(
                new Organization("Concurrency Org", "concurrency-org", Instant.now()));
        Resource court = resourceRepository.save(
                new Resource(organization.getId(), "Court 1", "Outdoor tennis court", Instant.now()));
        User booker = userRepository.save(
                new User(organization.getId(), "racer", passwordEncoder.encode("password123"), Role.USER, Instant.now()));
        AuthenticatedUser actor =
                new AuthenticatedUser(booker.getId(), booker.getUsername(), booker.getRole(), organization.getId());

        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");
        ReservationRequest sameSlot = new ReservationRequest(court.getId(), start, end);

        int attempts = 12;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                try {
                    reservationService.create(actor, sameSlot);
                    successes.incrementAndGet();
                } catch (SlotConflictException | ConcurrencyFailureException | DataIntegrityViolationException conflict) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(attempts - 1);
        assertThat(reservationRepository.findByResourceIdAndStatusOrderByStartTimeAsc(
                court.getId(), ReservationStatus.ACTIVE)).hasSize(1);
    }
}
