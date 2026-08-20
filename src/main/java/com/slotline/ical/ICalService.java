package com.slotline.ical;

import com.slotline.reservation.Reservation;
import com.slotline.reservation.ReservationRepository;
import com.slotline.reservation.ReservationStatus;
import com.slotline.resource.Resource;
import com.slotline.resource.ResourceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ICalService {

    private static final DateTimeFormatter icalTimestamp =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final String lineBreak = "\r\n";

    private final ReservationRepository reservations;
    private final ResourceService resources;

    public ICalService(ReservationRepository reservations, ResourceService resources) {
        this.reservations = reservations;
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public String forResource(Long organizationId, Long resourceId) {
        Resource resource = resources.require(organizationId, resourceId);
        List<Reservation> active = reservations
                .findByResourceIdAndStatusOrderByStartTimeAsc(resource.getId(), ReservationStatus.ACTIVE);
        return render("resource-" + resource.getId(), active);
    }

    @Transactional(readOnly = true)
    public String forUser(Long userId) {
        List<Reservation> active =
                reservations.findByUserIdAndStatusOrderByStartTimeAsc(userId, ReservationStatus.ACTIVE);
        return render("user-" + userId, active);
    }

    private String render(String feedName, List<Reservation> active) {
        StringBuilder calendar = new StringBuilder();
        calendar.append("BEGIN:VCALENDAR").append(lineBreak);
        calendar.append("VERSION:2.0").append(lineBreak);
        calendar.append("PRODID:-//slotline//reservation feed//EN").append(lineBreak);
        calendar.append("CALSCALE:GREGORIAN").append(lineBreak);
        calendar.append("X-WR-CALNAME:slotline ").append(feedName).append(lineBreak);
        String stamp = icalTimestamp.format(Instant.now());
        for (Reservation reservation : active) {
            calendar.append("BEGIN:VEVENT").append(lineBreak);
            calendar.append("UID:reservation-").append(reservation.getId()).append("@slotline").append(lineBreak);
            calendar.append("DTSTAMP:").append(stamp).append(lineBreak);
            calendar.append("DTSTART:").append(icalTimestamp.format(reservation.getStartTime())).append(lineBreak);
            calendar.append("DTEND:").append(icalTimestamp.format(reservation.getEndTime())).append(lineBreak);
            calendar.append("SUMMARY:").append(escape(reservation.getResource().getName())).append(lineBreak);
            calendar.append("END:VEVENT").append(lineBreak);
        }
        calendar.append("END:VCALENDAR").append(lineBreak);
        return calendar.toString();
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }
}
