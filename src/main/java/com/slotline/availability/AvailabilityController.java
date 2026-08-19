package com.slotline.availability;

import com.slotline.availability.dto.BlackoutRequest;
import com.slotline.availability.dto.BlackoutResponse;
import com.slotline.availability.dto.OperatingHoursRequest;
import com.slotline.availability.dto.OperatingHoursResponse;
import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources/{resourceId}")
@Tag(name = "Availability rules")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/operating-hours")
    @Operation(summary = "List operating hours for a resource")
    public List<OperatingHoursResponse> operatingHours(@AuthenticationPrincipal AuthenticatedUser actor,
                                                       @PathVariable Long resourceId) {
        return availabilityService.listOperatingHours(actor.organizationId(), resourceId).stream()
                .map(OperatingHoursResponse::from)
                .toList();
    }

    @PutMapping("/operating-hours")
    @Operation(summary = "Replace the operating-hours schedule for a resource (ADMIN only)")
    public List<OperatingHoursResponse> replaceOperatingHours(@AuthenticationPrincipal AuthenticatedUser actor,
                                                              @PathVariable Long resourceId,
                                                              @Valid @RequestBody List<OperatingHoursRequest> windows) {
        return availabilityService.replaceOperatingHours(actor.organizationId(), resourceId, windows).stream()
                .map(OperatingHoursResponse::from)
                .toList();
    }

    @GetMapping("/blackouts")
    @Operation(summary = "List blackout ranges for a resource")
    public List<BlackoutResponse> blackouts(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable Long resourceId) {
        return availabilityService.listBlackouts(actor.organizationId(), resourceId).stream()
                .map(BlackoutResponse::from)
                .toList();
    }

    @PostMapping("/blackouts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a blackout range for a resource (ADMIN only)")
    public BlackoutResponse addBlackout(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable Long resourceId,
                                        @Valid @RequestBody BlackoutRequest request) {
        return BlackoutResponse.from(availabilityService.addBlackout(actor.organizationId(), resourceId, request));
    }

    @DeleteMapping("/blackouts/{blackoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a blackout range (ADMIN only)")
    public void removeBlackout(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long resourceId,
                               @PathVariable Long blackoutId) {
        availabilityService.removeBlackout(actor.organizationId(), resourceId, blackoutId);
    }
}
