package com.slotline.recurrence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurrenceExpanderTest {

    private final RecurrenceExpander expander = new RecurrenceExpander();

    @Test
    void expandsDailyWithIntervalAndCount() {
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");

        List<Occurrence> occurrences = expander.expand(start, end, "FREQ=DAILY;INTERVAL=2;COUNT=3", ZoneOffset.UTC);

        assertThat(occurrences).extracting(Occurrence::start).containsExactly(
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-03T10:00:00Z"),
                Instant.parse("2026-09-05T10:00:00Z"));
        assertThat(occurrences).allSatisfy(occurrence ->
                assertThat(occurrence.end()).isEqualTo(occurrence.start().plusSeconds(3600)));
    }

    @Test
    void expandsWeeklyByDayInChronologicalOrder() {
        Instant start = Instant.parse("2026-09-07T10:00:00Z");
        Instant end = Instant.parse("2026-09-07T11:00:00Z");

        List<Occurrence> occurrences = expander.expand(start, end, "FREQ=WEEKLY;BYDAY=MO,WE;COUNT=4", ZoneOffset.UTC);

        assertThat(occurrences).extracting(Occurrence::start).containsExactly(
                Instant.parse("2026-09-07T10:00:00Z"),
                Instant.parse("2026-09-09T10:00:00Z"),
                Instant.parse("2026-09-14T10:00:00Z"),
                Instant.parse("2026-09-16T10:00:00Z"));
    }

    @Test
    void stopsAtUntilBoundary() {
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");

        List<Occurrence> occurrences = expander.expand(start, end, "FREQ=DAILY;UNTIL=20260903T100000Z", ZoneOffset.UTC);

        assertThat(occurrences).extracting(Occurrence::start).containsExactly(
                Instant.parse("2026-09-01T10:00:00Z"),
                Instant.parse("2026-09-02T10:00:00Z"),
                Instant.parse("2026-09-03T10:00:00Z"));
    }

    @Test
    void rejectsUnboundedRule() {
        Instant start = Instant.parse("2026-09-01T10:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:00:00Z");

        assertThatThrownBy(() -> expander.expand(start, end, "FREQ=DAILY", ZoneOffset.UTC))
                .isInstanceOf(RecurrenceException.class);
    }
}
