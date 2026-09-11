package com.slotline.reservation;

import com.slotline.common.SlotConflictException;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.RecurringReservationRequest;
import com.slotline.reservation.dto.RecurringReservationResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RecurringReservationTest {

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
    private ReservationRepository reservations;

    @Test
    void weeklySeriesExpandsIntoDistinctSlots() {
        AuthenticatedUser actor = tenant("recurring-ok");
        Long resourceId = resourceService.create(actor.organizationId(),
                new ResourceRequest("Meeting room", "weekly standup")).id();

        RecurringReservationResponse series = reservationService.createSeries(actor,
                new RecurringReservationRequest(resourceId,
                        Instant.parse("2026-09-07T10:00:00Z"),
                        Instant.parse("2026-09-07T11:00:00Z"),
                        "FREQ=WEEKLY;BYDAY=MO;COUNT=3"));

        assertThat(series.count()).isEqualTo(3);
        assertThat(series.reservations()).extracting(dto -> dto.startTime()).containsExactly(
                Instant.parse("2026-09-07T10:00:00Z"),
                Instant.parse("2026-09-14T10:00:00Z"),
                Instant.parse("2026-09-21T10:00:00Z"));
        assertThat(reservations.findBySeriesIdOrderByStartTimeAsc(series.seriesId())).hasSize(3);
    }

    @Test
    void conflictingSeriesIsRolledBackWhole() {
        AuthenticatedUser actor = tenant("recurring-conflict");
        Long resourceId = resourceService.create(actor.organizationId(),
                new ResourceRequest("Lab", "shared bench")).id();

        reservationService.create(actor, new ReservationRequest(resourceId,
                Instant.parse("2026-09-14T10:00:00Z"),
                Instant.parse("2026-09-14T11:00:00Z")));

        assertThatThrownBy(() -> reservationService.createSeries(actor,
                new RecurringReservationRequest(resourceId,
                        Instant.parse("2026-09-07T10:00:00Z"),
                        Instant.parse("2026-09-07T11:00:00Z"),
                        "FREQ=WEEKLY;BYDAY=MO;COUNT=3")))
                .isInstanceOf(SlotConflictException.class);

        assertThat(reservations.findByResourceIdAndStatusOrderByStartTimeAsc(resourceId, ReservationStatus.ACTIVE))
                .hasSize(1);
    }

    private AuthenticatedUser tenant(String slug) {
        Organization organization = organizations.save(new Organization(slug, slug, Instant.now()));
        User user = users.save(new User(organization.getId(), slug + "-user",
                passwordEncoder.encode("password123"), Role.USER, Instant.now()));
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), organization.getId());
    }
}
