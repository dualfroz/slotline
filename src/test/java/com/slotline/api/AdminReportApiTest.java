package com.slotline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.resource.dto.ResourceRequest;
import com.slotline.user.Role;
import com.slotline.user.User;
import com.slotline.user.UserRepository;
import com.slotline.user.dto.LoginRequest;
import com.slotline.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminReportApiTest {

    private static final String WINDOW = "from=2027-01-04T10:00:00Z&until=2027-01-04T14:00:00Z";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void occupancyClampsToTheWindowAndHistoryKeepsCancelledSlots() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("report-user", "password123"))))
                .andExpect(status().isCreated());

        String userToken = login("report-user", "password123");
        String adminToken = login("admin", "admin12345");
        long resourceId = createResource(adminToken, "Report room");

        book(userToken, resourceId, "2027-01-04T09:30:00Z", "2027-01-04T10:30:00Z");
        book(userToken, resourceId, "2027-01-04T12:00:00Z", "2027-01-04T13:00:00Z");
        long cancelledId = book(userToken, resourceId, "2027-01-04T13:00:00Z", "2027-01-04T14:00:00Z");
        book(userToken, resourceId, "2027-01-04T15:00:00Z", "2027-01-04T16:00:00Z");

        mockMvc.perform(post("/api/reservations/" + cancelledId + "/cancel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/occupancy?" + WINDOW)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(resourceId))
                .andExpect(jsonPath("$.resourceName").value("Report room"))
                .andExpect(jsonPath("$.reservationCount").value(2))
                .andExpect(jsonPath("$.bookedMinutes").value(90))
                .andExpect(jsonPath("$.windowMinutes").value(240))
                .andExpect(jsonPath("$.utilizationPercent").value(37.5));

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/history")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].startTime").value("2027-01-04T09:30:00Z"))
                .andExpect(jsonPath("$[2].id").value(cancelledId))
                .andExpect(jsonPath("$[2].status").value("CANCELLED"))
                .andExpect(jsonPath("$[3].status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/occupancy?" + WINDOW)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportsAreScopedToTheCallerOrganization() throws Exception {
        Organization other = organizations.save(new Organization("Reports Other", "reports-other", Instant.now()));
        users.save(new User(other.getId(), "reports-other-admin", passwordEncoder.encode("password123"), Role.ADMIN,
                Instant.now()));

        String adminToken = login("admin", "admin12345");
        String otherAdminToken = login("reports-other-admin", "password123");
        long resourceId = createResource(adminToken, "Private room");

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/occupancy?" + WINDOW)
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/reports/resources/" + resourceId + "/history")
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isNotFound());
    }

    private long createResource(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ResourceRequest(name, "reported resource"))))
                .andExpect(status().isCreated())
                .andReturn();
        return tree(created).get("id").asLong();
    }

    private long book(String token, long resourceId, String start, String end) throws Exception {
        MvcResult booked = mockMvc.perform(post("/api/reservations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReservationRequest(resourceId, Instant.parse(start), Instant.parse(end)))))
                .andExpect(status().isCreated())
                .andReturn();
        return tree(booked).get("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return tree(result).get("token").asText();
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private JsonNode tree(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
