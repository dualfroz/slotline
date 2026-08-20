package com.slotline.ical;

import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "iCal export")
public class ICalController {

    private static final MediaType calendarType = MediaType.parseMediaType("text/calendar;charset=UTF-8");

    private final ICalService icalService;

    public ICalController(ICalService icalService) {
        this.icalService = icalService;
    }

    @GetMapping("/api/resources/{resourceId}/calendar.ics")
    @Operation(summary = "iCal (.ics) feed of active reservations for a resource")
    public ResponseEntity<String> forResource(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable Long resourceId) {
        String body = icalService.forResource(actor.organizationId(), resourceId);
        return calendar(body, "resource-" + resourceId + ".ics");
    }

    @GetMapping("/api/reservations/calendar.ics")
    @Operation(summary = "iCal (.ics) feed of the caller's active reservations")
    public ResponseEntity<String> forCaller(@AuthenticationPrincipal AuthenticatedUser actor) {
        String body = icalService.forUser(actor.id());
        return calendar(body, "my-reservations.ics");
    }

    private ResponseEntity<String> calendar(String body, String filename) {
        return ResponseEntity.ok()
                .contentType(calendarType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
