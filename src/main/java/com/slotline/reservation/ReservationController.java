package com.slotline.reservation;

import com.slotline.reservation.dto.RecurringReservationRequest;
import com.slotline.reservation.dto.RecurringReservationResponse;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.reservation.dto.ReservationResponse;
import com.slotline.reservation.dto.WaitlistRequest;
import com.slotline.reservation.dto.WaitlistResponse;
import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final WaitlistService waitlistService;

    public ReservationController(ReservationService reservationService, WaitlistService waitlistService) {
        this.reservationService = reservationService;
        this.waitlistService = waitlistService;
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a slot on a resource (409 on conflict)")
    public ReservationResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @Valid @RequestBody ReservationRequest request) {
        return reservationService.create(actor, request);
    }

    @PostMapping("/reservations/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a recurring series expanded from an RRULE (all-or-nothing)")
    public RecurringReservationResponse createSeries(@AuthenticationPrincipal AuthenticatedUser actor,
                                                     @Valid @RequestBody RecurringReservationRequest request) {
        return reservationService.createSeries(actor, request);
    }

    @GetMapping("/reservations/mine")
    @Operation(summary = "List the caller's reservations")
    public List<ReservationResponse> mine(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reservationService.listForUser(actor.id());
    }

    @PostMapping("/reservations/{id}/cancel")
    @Operation(summary = "Cancel a reservation (owner or ADMIN); promotes the waitlist")
    public ReservationResponse cancel(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return reservationService.cancel(actor, id);
    }

    @PostMapping("/reservations/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Join the waitlist for a slot")
    public WaitlistResponse joinWaitlist(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @Valid @RequestBody WaitlistRequest request) {
        return waitlistService.join(actor, request);
    }

    @GetMapping("/reservations/waitlist/mine")
    @Operation(summary = "List the caller's waitlist entries")
    public List<WaitlistResponse> myWaitlist(@AuthenticationPrincipal AuthenticatedUser actor) {
        return waitlistService.listForUser(actor.id());
    }

    @GetMapping("/resources/{resourceId}/reservations")
    @Operation(summary = "List active reservations for a resource")
    public List<ReservationResponse> forResource(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable Long resourceId) {
        return reservationService.listActiveForResource(actor, resourceId);
    }
}
