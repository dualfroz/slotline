package com.slotline.report;

import com.slotline.report.dto.OccupancyReport;
import com.slotline.report.dto.ReservationHistoryEntry;
import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@Tag(name = "Admin reports")
public class AdminReportController {

    private final AdminReportService reportService;

    public AdminReportController(AdminReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/resources/{resourceId}/occupancy")
    @Operation(summary = "Occupancy of a resource over a time window (ADMIN only)")
    public OccupancyReport occupancy(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long resourceId,
                                     @RequestParam String from, @RequestParam String until) {
        return reportService.occupancy(actor.organizationId(), resourceId, Instant.parse(from), Instant.parse(until));
    }

    @GetMapping("/resources/{resourceId}/history")
    @Operation(summary = "Full reservation history of a resource (ADMIN only)")
    public List<ReservationHistoryEntry> history(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable Long resourceId) {
        return reportService.history(actor.organizationId(), resourceId);
    }
}
