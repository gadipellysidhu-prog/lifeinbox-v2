package com.lifeinbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.SecurityConfig;
import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.ReminderResponse;
import com.lifeinbox.dto.UpdateReminderRequest;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.security.CustomUserDetailsService;
import com.lifeinbox.security.JwtAuthenticationFilter;
import com.lifeinbox.security.JwtProperties;
import com.lifeinbox.security.JwtService;
import com.lifeinbox.security.RestAccessDeniedHandler;
import com.lifeinbox.security.RestAuthenticationEntryPoint;
import com.lifeinbox.security.UserPrincipal;
import com.lifeinbox.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReminderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, JwtProperties.class})
class ReminderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReminderService reminderService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID userId;
    private UUID reminderId;
    private UserPrincipal samplePrincipal;
    private ReminderResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reminderId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("pwd")
                .build();

        samplePrincipal = UserPrincipal.create(user);

        sampleResponse = new ReminderResponse(
                reminderId,
                "Dentist Appointment",
                "Bring card",
                Instant.now().plusSeconds(3600),
                ReminderPriority.HIGH,
                "Health",
                ReminderStatus.PENDING,
                Instant.now(),
                Instant.now(),
                null
        );

        when(jwtService.validateToken("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(samplePrincipal);
    }

    @Test
    @DisplayName("GET /api/reminders: 401 without Bearer token")
    void testGetRemindersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/reminders: 200 with Bearer token")
    void testGetRemindersAuthenticated() throws Exception {
        when(reminderService.getReminders(eq(userId), eq("all"), eq(null))).thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/reminders")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reminderId.toString()))
                .andExpect(jsonPath("$[0].title").value("Dentist Appointment"));
    }

    @Test
    @DisplayName("GET /api/reminders/{id}: 200 when found")
    void testGetReminderByIdFound() throws Exception {
        when(reminderService.getReminderById(reminderId, userId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/reminders/{id}", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminderId.toString()));
    }

    @Test
    @DisplayName("GET /api/reminders/{id}: 404 when not owned by user")
    void testGetReminderByIdNotFound() throws Exception {
        when(reminderService.getReminderById(reminderId, userId))
                .thenThrow(new ResourceNotFoundException("Reminder not found with id: " + reminderId));

        mockMvc.perform(get("/api/reminders/{id}", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("POST /api/reminders: 201 on valid payload")
    void testCreateReminderSuccess() throws Exception {
        CreateReminderRequest request = new CreateReminderRequest(
                "Dentist Appointment",
                "Bring card",
                Instant.now().plusSeconds(3600),
                ReminderPriority.HIGH,
                "Health"
        );

        when(reminderService.createReminder(any(CreateReminderRequest.class), eq(userId))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reminderId.toString()));
    }

    @Test
    @DisplayName("PUT /api/reminders/{id}: 200 on valid update")
    void testUpdateReminderSuccess() throws Exception {
        UpdateReminderRequest request = new UpdateReminderRequest(
                "Updated Title",
                "Updated notes",
                Instant.now().plusSeconds(7200),
                ReminderPriority.LOW,
                "General"
        );

        when(reminderService.updateReminder(eq(reminderId), any(UpdateReminderRequest.class), eq(userId))).thenReturn(sampleResponse);

        mockMvc.perform(put("/api/reminders/{id}", reminderId)
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminderId.toString()));
    }

    @Test
    @DisplayName("PATCH /api/reminders/{id}/complete: 200 on success")
    void testCompleteReminderSuccess() throws Exception {
        ReminderResponse completedResponse = new ReminderResponse(
                reminderId,
                "Dentist Appointment",
                "Bring card",
                Instant.now(),
                ReminderPriority.HIGH,
                "Health",
                ReminderStatus.COMPLETED,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(reminderService.completeReminder(reminderId, userId)).thenReturn(completedResponse);

        mockMvc.perform(patch("/api/reminders/{id}/complete", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/reminders/{id}/incomplete: 200 on success")
    void testIncompleteReminderSuccess() throws Exception {
        when(reminderService.incompleteReminder(reminderId, userId)).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/reminders/{id}/incomplete", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("DELETE /api/reminders/{id}: 204 on success")
    void testDeleteReminderSuccess() throws Exception {
        mockMvc.perform(delete("/api/reminders/{id}", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNoContent());

        verify(reminderService).deleteReminder(reminderId, userId);
    }

    @Test
    @DisplayName("DELETE /api/reminders/{id}: 404 when not owned by user")
    void testDeleteReminderNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Reminder not found with id: " + reminderId))
                .when(reminderService).deleteReminder(reminderId, userId);

        mockMvc.perform(delete("/api/reminders/{id}", reminderId)
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
