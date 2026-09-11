package com.slotline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.resource.dto.ResourceRequest;
import com.slotline.user.dto.LoginRequest;
import com.slotline.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ICalExportTest {

    private static final Instant SLOT_START = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant SLOT_END = Instant.parse("2026-12-01T11:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void resourceAndUserFeedsExposeBookedSlotsAsVevents() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("ical-user", "password123"))))
                .andExpect(status().isCreated());

        String userToken = login("ical-user", "password123");
        String adminToken = login("admin", "admin12345");

        MvcResult created = mockMvc.perform(post("/api/resources")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResourceRequest("Ical room", "feed me"))))
                .andExpect(status().isCreated())
                .andReturn();
        long resourceId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReservationRequest(resourceId, SLOT_START, SLOT_END))))
                .andExpect(status().isCreated());

        String resourceFeed = mockMvc.perform(get("/api/resources/" + resourceId + "/calendar.ics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(resourceFeed).contains("BEGIN:VCALENDAR");
        assertThat(resourceFeed).contains("DTSTART:20261201T100000Z");
        assertThat(resourceFeed).contains("DTEND:20261201T110000Z");
        assertThat(countOccurrences(resourceFeed, "BEGIN:VEVENT")).isEqualTo(1);

        String userFeed = mockMvc.perform(get("/api/reservations/calendar.ics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(countOccurrences(userFeed, "BEGIN:VEVENT")).isEqualTo(1);
        assertThat(userFeed).contains("SUMMARY:Ical room");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("token").asText();
    }
}
