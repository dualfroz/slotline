package com.slotline.report;

import com.slotline.report.dto.OccupancyReport;
import com.slotline.report.dto.ReservationHistoryEntry;
import com.slotline.reservation.Reservation;
import com.slotline.reservation.ReservationRepository;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AdminReportService {

    private final ReservationRepository reservations;
    private final ResourceService resources;

    public AdminReportService(ReservationRepository reservations, ResourceService resources) {
        this.reservations = reservations;
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public OccupancyReport occupancy(Long organizationId, Long resourceId, Instant from, Instant until) {
        Resource resource = resources.require(organizationId, resourceId);
        List<Reservation> active = reservations.findActiveForResourceBetween(resource.getId(), from, until);
        long bookedMinutes = active.stream()
                .mapToLong(reservation -> clampedMinutes(reservation, from, until))
                .sum();
        long windowMinutes = Duration.between(from, until).toMinutes();
        double utilization = windowMinutes > 0 ? (bookedMinutes * 100.0) / windowMinutes : 0.0;
        return new OccupancyReport(resource.getId(), resource.getName(), from, until, active.size(), bookedMinutes,
                windowMinutes, Math.round(utilization * 100.0) / 100.0);
    }

    @Transactional(readOnly = true)
    public List<ReservationHistoryEntry> history(Long organizationId, Long resourceId) {
        Resource resource = resources.require(organizationId, resourceId);
        return reservations.findByResourceIdOrderByStartTimeAsc(resource.getId()).stream()
                .map(ReservationHistoryEntry::from)
                .toList();
    }

    private long clampedMinutes(Reservation reservation, Instant from, Instant until) {
        Instant start = reservation.getStartTime().isBefore(from) ? from : reservation.getStartTime();
        Instant end = reservation.getEndTime().isAfter(until) ? until : reservation.getEndTime();
        long minutes = Duration.between(start, end).toMinutes();
        return Math.max(minutes, 0);
    }
}
