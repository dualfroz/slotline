package com.slotline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.reservation.dto.ReservationRequest;
import com.slotline.resource.ResourceService;
import com.slotline.resource.dto.ResourceRequest;
import com.slotline.user.Role;
import com.slotline.user.User;
import com.slotline.user.UserRepository;
import com.slotline.user.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationTest {

    private static final Instant SLOT_START = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant SLOT_END = Instant.parse("2026-11-01T11:00:00Z");

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

    @Autowired
    private ResourceService resourceService;

    @Test
    void resourcesAndBookingsAreIsolatedPerTenant() throws Exception {
        Organization alpha = organizations.save(new Organization("Alpha", "alpha", Instant.now()));
        Organization beta = organizations.save(new Organization("Beta", "beta", Instant.now()));
        seedUser(alpha.getId(), "alpha-user");
        seedUser(beta.getId(), "beta-user");
        Long alphaResourceId = resourceService.create(alpha.getId(),
                new ResourceRequest("Alpha court", "belongs to alpha")).id();
        resourceService.create(beta.getId(), new ResourceRequest("Beta court", "belongs to beta"));

        String alphaToken = login("alpha-user");
        String betaToken = login("beta-user");

        mockMvc.perform(get("/api/resources/" + alphaResourceId).header("Authorization", "Bearer " + alphaToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/resources/" + alphaResourceId).header("Authorization", "Bearer " + betaToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/resources").header("Authorization", "Bearer " + betaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Beta court"));

        String booking = objectMapper.writeValueAsString(
                new ReservationRequest(alphaResourceId, SLOT_START, SLOT_END));

        mockMvc.perform(post("/api/reservations").header("Authorization", "Bearer " + betaToken)
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reservations").header("Authorization", "Bearer " + alphaToken)
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated());
    }

    private void seedUser(Long organizationId, String username) {
        users.save(new User(organizationId, username, passwordEncoder.encode("password123"), Role.USER,
                Instant.now()));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("token").asText();
    }
}
