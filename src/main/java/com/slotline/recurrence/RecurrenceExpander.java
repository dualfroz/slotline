package com.slotline.recurrence;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecurrenceExpander {

    public static final int maxOccurrences = 366;

    private static final DateTimeFormatter untilFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private static final Map<String, DayOfWeek> byDayCodes = Map.of(
            "MO", DayOfWeek.MONDAY,
            "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY,
            "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    public List<Occurrence> expand(Instant firstStart, Instant firstEnd, String rrule, ZoneId zone) {
        Duration duration = Duration.between(firstStart, firstEnd);
        if (duration.isZero() || duration.isNegative()) {
            throw new RecurrenceException("recurrence slot duration must be positive");
        }
        Map<String, String> parts = parse(rrule);
        String frequency = required(parts, "FREQ");
        int interval = parseInterval(parts.get("INTERVAL"));
        Integer count = parseCount(parts.get("COUNT"));
        Instant until = parseUntil(parts.get("UNTIL"));
        if (count == null && until == null) {
            throw new RecurrenceException("recurrence must specify COUNT or UNTIL");
        }

        return switch (frequency) {
            case "DAILY" -> expandFixedStep(firstStart, duration, zone, interval, count, until, true);
            case "WEEKLY" -> parts.containsKey("BYDAY")
                    ? expandWeeklyByDay(firstStart, duration, zone, interval, count, until, parseByDay(parts.get("BYDAY")))
                    : expandFixedStep(firstStart, duration, zone, interval, count, until, false);
            default -> throw new RecurrenceException("unsupported FREQ '" + frequency + "'");
        };
    }

    private List<Occurrence> expandFixedStep(Instant firstStart, Duration duration, ZoneId zone, int interval,
                                             Integer count, Instant until, boolean daily) {
        List<Occurrence> occurrences = new ArrayList<>();
        ZonedDateTime cursor = firstStart.atZone(zone);
        while (true) {
            Instant start = cursor.toInstant();
            if (until != null && start.isAfter(until)) {
                break;
            }
            occurrences.add(new Occurrence(start, start.plus(duration)));
            if (reachedLimit(occurrences.size(), count)) {
                break;
            }
            cursor = daily ? cursor.plusDays(interval) : cursor.plusWeeks(interval);
        }
        return occurrences;
    }

    private List<Occurrence> expandWeeklyByDay(Instant firstStart, Duration duration, ZoneId zone, int interval,
                                               Integer count, Instant until, List<DayOfWeek> days) {
        List<Occurrence> occurrences = new ArrayList<>();
        ZonedDateTime firstZoned = firstStart.atZone(zone);
        LocalTime timeOfDay = firstZoned.toLocalTime();
        LocalDate firstDate = firstZoned.toLocalDate();
        LocalDate baseMonday = firstDate.minusDays(firstDate.getDayOfWeek().getValue() - 1L);

        int weekBlock = 0;
        while (weekBlock <= maxOccurrences) {
            LocalDate weekMonday = baseMonday.plusWeeks((long) weekBlock * interval);
            for (DayOfWeek day : days) {
                LocalDate date = weekMonday.plusDays(day.getValue() - 1L);
                Instant start = ZonedDateTime.of(date, timeOfDay, zone).toInstant();
                if (start.isBefore(firstStart)) {
                    continue;
                }
                if (until != null && start.isAfter(until)) {
                    return occurrences;
                }
                occurrences.add(new Occurrence(start, start.plus(duration)));
                if (reachedLimit(occurrences.size(), count)) {
                    return occurrences;
                }
            }
            weekBlock++;
        }
        return occurrences;
    }

    private boolean reachedLimit(int size, Integer count) {
        if (count != null && size >= count) {
            return true;
        }
        return size >= maxOccurrences;
    }

    private Map<String, String> parse(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            throw new RecurrenceException("recurrence rule is empty");
        }
        Map<String, String> parts = new HashMap<>();
        for (String token : rrule.trim().toUpperCase().split(";")) {
            if (token.isBlank()) {
                continue;
            }
            int split = token.indexOf('=');
            if (split <= 0) {
                throw new RecurrenceException("malformed recurrence token '" + token + "'");
            }
            parts.put(token.substring(0, split), token.substring(split + 1));
        }
        return parts;
    }

    private String required(Map<String, String> parts, String key) {
        String value = parts.get(key);
        if (value == null || value.isBlank()) {
            throw new RecurrenceException("recurrence rule is missing " + key);
        }
        return value;
    }

    private int parseInterval(String raw) {
        if (raw == null) {
            return 1;
        }
        int interval = parsePositive(raw, "INTERVAL");
        return interval;
    }

    private Integer parseCount(String raw) {
        return raw == null ? null : parsePositive(raw, "COUNT");
    }

    private int parsePositive(String raw, String key) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new RecurrenceException(key + " must be at least 1");
            }
            return value;
        } catch (NumberFormatException notANumber) {
            throw new RecurrenceException(key + " must be an integer");
        }
    }

    private Instant parseUntil(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), untilFormat).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException malformed) {
            throw new RecurrenceException("UNTIL must be UTC basic format yyyyMMdd'T'HHmmss'Z'");
        }
    }

    private List<DayOfWeek> parseByDay(String raw) {
        List<DayOfWeek> days = Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(code -> {
                    DayOfWeek day = byDayCodes.get(code);
                    if (day == null) {
                        throw new RecurrenceException("unknown BYDAY code '" + code + "'");
                    }
                    return day;
                })
                .distinct()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .toList();
        if (days.isEmpty()) {
            throw new RecurrenceException("BYDAY must list at least one day");
        }
        return days;
    }
}
