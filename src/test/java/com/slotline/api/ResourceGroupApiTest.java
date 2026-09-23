package com.slotline.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotline.organization.Organization;
import com.slotline.organization.OrganizationRepository;
import com.slotline.resource.dto.ResourceGroupRequest;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResourceGroupApiTest {

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
    void adminBuildsNestedGroupsAndAssignsResources() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new RegisterRequest("group-user", "password123"))))
                .andExpect(status().isCreated());

        String userToken = login("group-user");
        String adminToken = login("admin", "admin12345");

        long sportsId = idOf(createGroup(adminToken, new ResourceGroupRequest("Sports", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sports"))
                .andExpect(jsonPath("$.parentGroupId").value(nullValue())));

        long tennisId = idOf(createGroup(adminToken, new ResourceGroupRequest("Tennis", sportsId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentGroupId").value(sportsId)));

        createGroup(adminToken, new ResourceGroupRequest("Orphan", Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        createGroup(adminToken, new ResourceGroupRequest(" ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("name"));

        createGroup(userToken, new ResourceGroupRequest("Not allowed", null))
                .andExpect(status().isForbidden());

        long resourceId = createResource(adminToken, "Court 7");

        mockMvc.perform(post("/api/resource-groups/" + tennisId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/resource-groups/" + tennisId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resourceId))
                .andExpect(jsonPath("$.groupId").value(tennisId));

        mockMvc.perform(get("/api/resource-groups/" + tennisId + "/resources")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Court 7"));

        mockMvc.perform(get("/api/resource-groups/" + sportsId + "/resources")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/resource-groups").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItems("Sports", "Tennis")));
    }

    @Test
    void groupsAreInvisibleToOtherOrganizations() throws Exception {
        Organization other = organizations.save(new Organization("Groups Other", "groups-other", Instant.now()));
        users.save(new User(other.getId(), "groups-other-admin", passwordEncoder.encode("password123"), Role.ADMIN,
                Instant.now()));

        String adminToken = login("admin", "admin12345");
        String otherAdminToken = login("groups-other-admin");

        long groupId = idOf(createGroup(adminToken, new ResourceGroupRequest("Default rooms", null))
                .andExpect(status().isCreated()));
        long resourceId = createResource(adminToken, "Default room");
        long otherResourceId = createResource(otherAdminToken, "Other room");

        mockMvc.perform(get("/api/resource-groups").header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/resource-groups/" + groupId + "/resources")
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/resource-groups/" + groupId + "/resources/" + resourceId)
                        .header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isNotFound());

        createGroup(otherAdminToken, new ResourceGroupRequest("Nested elsewhere", groupId))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/resource-groups/" + groupId + "/resources/" + otherResourceId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private ResultActions createGroup(String token, ResourceGroupRequest request) throws Exception {
        return mockMvc.perform(post("/api/resource-groups")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(request)));
    }

    private long createResource(String token, String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ResourceRequest(name, "grouped resource"))))
                .andExpect(status().isCreated()));
    }

    private long idOf(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String login(String username) throws Exception {
        return login(username, "password123");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
        return tree.get("token").asText();
    }
}
