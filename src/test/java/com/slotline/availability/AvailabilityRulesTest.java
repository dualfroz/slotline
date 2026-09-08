package com.slotline.availability;

import com.slotline.availability.dto.BlackoutRequest;
import com.slotline.availability.dto.OperatingHoursRequest;
import com.slotline.common.AvailabilityViolationException;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.ReservationService;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AvailabilityRulesTest {

    private static final ZoneId zone = ZoneId.of("Europe/Warsaw");
    private static final LocalDate monday = LocalDate.of(2026, 9, 7);

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AvailabilityService availabilityService;

    @Autowired
    private ReservationService reservationService;

    @Test
    void operatingHoursAndBufferGovernBooking() {
        AuthenticatedUser actor = tenant("availability-hours");
        Long resourceId = resourceService.create(actor.organizationId(),
                new ResourceRequest("Studio", "with hours", zone.getId(), 30)).id();
        availabilityService.replaceOperatingHours(actor.organizationId(), resourceId,
                List.of(new OperatingHoursRequest(monday.getDayOfWeek(), LocalTime.of(9, 0), LocalTime.of(17, 0))));

        assertThatCode(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(monday, 11, 15), at(monday, 12, 0))))
                .isInstanceOf(AvailabilityViolationException.class);

        assertThatThrownBy(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(monday, 18, 0), at(monday, 19, 0))))
                .isInstanceOf(AvailabilityViolationException.class);

        LocalDate closedDay = monday.plusDays(1);
        assertThatThrownBy(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(closedDay, 10, 0), at(closedDay, 11, 0))))
                .isInstanceOf(AvailabilityViolationException.class);
    }

    @Test
    void blackoutRangeBlocksBooking() {
        AuthenticatedUser actor = tenant("availability-blackout");
        Long resourceId = resourceService.create(actor.organizationId(),
                new ResourceRequest("Court", "blacked out", zone.getId(), 0)).id();
        availabilityService.addBlackout(actor.organizationId(), resourceId,
                new BlackoutRequest(monday, monday, "maintenance"));

        assertThatThrownBy(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(monday, 10, 0), at(monday, 11, 0))))
                .isInstanceOf(AvailabilityViolationException.class);

        LocalDate freeDay = monday.plusDays(3);
        assertThatCode(() -> reservationService.create(actor,
                new ReservationRequest(resourceId, at(freeDay, 10, 0), at(freeDay, 11, 0))))
                .doesNotThrowAnyException();
    }

    private AuthenticatedUser tenant(String slug) {
        Organization organization = organizations.save(new Organization(slug, slug, Instant.now()));
        User user = users.save(new User(organization.getId(), slug + "-user",
                passwordEncoder.encode("password123"), Role.USER, Instant.now()));
        assertThat(monday.getDayOfWeek().getValue()).isEqualTo(1);
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole(), organization.getId());
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant();
    }
}
