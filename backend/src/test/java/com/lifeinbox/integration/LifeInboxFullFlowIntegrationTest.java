package com.lifeinbox.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.LoginRequest;
import com.lifeinbox.dto.RegisterRequest;
import com.lifeinbox.dto.UpdateReminderRequest;
import com.lifeinbox.entity.ReminderPriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LifeInboxFullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete end-to-end integration: Auth, Multi-user isolation, Reminder CRUD, Filters, and Status transitions")
    void testCompleteApplicationLifecycle() throws Exception {
        // 1. Register User A
        RegisterRequest registerUserA = new RegisterRequest("User A", "user.a@example.com", "Password123!");
        MvcResult regResultA = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUserA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value("user.a@example.com"))
                .andReturn();

        JsonNode regNodeA = objectMapper.readTree(regResultA.getResponse().getContentAsString());
        String tokenA = regNodeA.get("accessToken").asText();
        String userAId = regNodeA.get("user").get("id").asText();

        // 2. Reject duplicate registration for User A
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUserA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // 3. Login User A
        LoginRequest loginUserA = new LoginRequest("user.a@example.com", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginUserA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.id").value(userAId));

        // 4. Reject login with invalid password
        LoginRequest badPasswordRequest = new LoginRequest("user.a@example.com", "WrongPassword!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPasswordRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 5. Authenticated GET /api/auth/me for User A
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user.a@example.com"))
                .andExpect(jsonPath("$.name").value("User A"));

        // 6. Unauthenticated GET /api/auth/me returns 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 7. Register User B
        RegisterRequest registerUserB = new RegisterRequest("User B", "user.b@example.com", "Password123!");
        MvcResult regResultB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUserB)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regNodeB = objectMapper.readTree(regResultB.getResponse().getContentAsString());
        String tokenB = regNodeB.get("accessToken").asText();

        // 8. User A creates Reminder 1 (Overdue / Past date)
        Instant pastDue = Instant.now().minus(2, ChronoUnit.DAYS);
        CreateReminderRequest createReminder1 = new CreateReminderRequest(
                "Car Inspection",
                "Overdue annual DMV inspection",
                pastDue,
                ReminderPriority.HIGH,
                "Auto"
        );
        MvcResult remResult1 = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReminder1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Car Inspection"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String reminder1Id = objectMapper.readTree(remResult1.getResponse().getContentAsString()).get("id").asText();

        // 9. User A creates Reminder 2 (Upcoming / Future date)
        Instant futureDue = Instant.now().plus(5, ChronoUnit.DAYS);
        CreateReminderRequest createReminder2 = new CreateReminderRequest(
                "Dentist Appointment",
                "Routine checkup",
                futureDue,
                ReminderPriority.MEDIUM,
                "Health"
        );
        mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReminder2)))
                .andExpect(status().isCreated());

        // 10. User A retrieves Reminder 1
        mockMvc.perform(get("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminder1Id));

        // ==========================================
        // MULTI-TENANT ISOLATION CHECKS (CRITICAL)
        // User B attempts operations on User A's Reminder 1 -> All MUST return 404
        // ==========================================
        mockMvc.perform(get("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        UpdateReminderRequest unauthorizedUpdate = new UpdateReminderRequest(
                "Hacked Title",
                "Hacked notes",
                Instant.now().plus(1, ChronoUnit.DAYS),
                ReminderPriority.LOW,
                "Hacked"
        );
        mockMvc.perform(put("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unauthorizedUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(patch("/api/reminders/" + reminder1Id + "/complete")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(patch("/api/reminders/" + reminder1Id + "/incomplete")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // User B sees 0 reminders in their list
        MvcResult userBList = mockMvc.perform(get("/api/reminders?filter=all")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(userBList.getResponse().getContentAsString()).size()).isEqualTo(0);

        // ==========================================
        // REMINDER LIFECYCLE & FILTER VERIFICATION
        // ==========================================
        // User A list all -> count 2
        MvcResult userAList = mockMvc.perform(get("/api/reminders?filter=all")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(userAList.getResponse().getContentAsString()).size()).isEqualTo(2);

        // Search by title "Inspection" -> count 1
        MvcResult searchResult = mockMvc.perform(get("/api/reminders?search=Inspection")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode searchNode = objectMapper.readTree(searchResult.getResponse().getContentAsString());
        assertThat(searchNode.size()).isEqualTo(1);
        assertThat(searchNode.get(0).get("title").asText()).isEqualTo("Car Inspection");

        // Filter overdue -> count 1 (Reminder 1 is past due)
        MvcResult overdueResult = mockMvc.perform(get("/api/reminders?filter=overdue")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(overdueResult.getResponse().getContentAsString()).size()).isEqualTo(1);

        // Filter upcoming -> count 1 (Reminder 2 is future due)
        MvcResult upcomingResult = mockMvc.perform(get("/api/reminders?filter=upcoming")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(upcomingResult.getResponse().getContentAsString()).size()).isEqualTo(1);

        // Mark Reminder 1 as complete
        mockMvc.perform(patch("/api/reminders/" + reminder1Id + "/complete")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").exists());

        // Filter completed -> count 1
        MvcResult completedResult = mockMvc.perform(get("/api/reminders?filter=completed")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(completedResult.getResponse().getContentAsString()).size()).isEqualTo(1);

        // Mark Reminder 1 as incomplete
        mockMvc.perform(patch("/api/reminders/" + reminder1Id + "/incomplete")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        // Update Reminder 1
        UpdateReminderRequest updateReq = new UpdateReminderRequest(
                "Car & Motorcycle Inspection",
                "Updated details",
                pastDue,
                ReminderPriority.HIGH,
                "Vehicles"
        );
        mockMvc.perform(put("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Car & Motorcycle Inspection"))
                .andExpect(jsonPath("$.category").value("Vehicles"));

        // Delete Reminder 1
        mockMvc.perform(delete("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // Verify Reminder 1 is 404 after delete
        mockMvc.perform(get("/api/reminders/" + reminder1Id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Final list count for User A is now 1
        MvcResult finalAList = mockMvc.perform(get("/api/reminders?filter=all")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(finalAList.getResponse().getContentAsString()).size()).isEqualTo(1);
    }
}
