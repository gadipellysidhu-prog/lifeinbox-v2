package com.lifeinbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.SecurityConfig;
import com.lifeinbox.dto.NotificationPreferencesResponse;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.entity.User;
import com.lifeinbox.security.CustomUserDetailsService;
import com.lifeinbox.security.JwtAuthenticationFilter;
import com.lifeinbox.security.JwtProperties;
import com.lifeinbox.security.JwtService;
import com.lifeinbox.security.RestAccessDeniedHandler;
import com.lifeinbox.security.RestAuthenticationEntryPoint;
import com.lifeinbox.security.UserPrincipal;
import com.lifeinbox.service.NotificationPreferencesService;
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
import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationPreferencesController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, JwtProperties.class})
class NotificationPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationPreferencesService preferencesService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID userId;
    private UserPrincipal samplePrincipal;
    private NotificationPreferencesResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("pwd")
                .build();

        samplePrincipal = UserPrincipal.create(user);

        sampleResponse = new NotificationPreferencesResponse(
                UUID.randomUUID(),
                true,
                true,
                true,
                LocalTime.of(8, 0),
                "Asia/Kolkata",
                Instant.now()
        );

        when(jwtService.validateToken("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(samplePrincipal);
    }

    @Test
    @DisplayName("GET /api/notification-preferences: 401 without Bearer token")
    void testGetPreferencesUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/notification-preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/notification-preferences: 200 with Bearer token")
    void testGetPreferencesSuccess() throws Exception {
        when(preferencesService.getPreferences(userId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/notification-preferences")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.reminderEmailsEnabled").value(true))
                .andExpect(jsonPath("$.dailyDigestEnabled").value(true))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.pushNotificationsEnabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/notification-preferences: 200 with valid update payload including pushNotificationsEnabled")
    void testUpdatePreferencesSuccess() throws Exception {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                false,
                true,
                false,
                LocalTime.of(9, 0),
                "Europe/London",
                false
        );

        NotificationPreferencesResponse updatedResponse = new NotificationPreferencesResponse(
                sampleResponse.id(),
                false,
                true,
                false,
                LocalTime.of(9, 0),
                "Europe/London",
                Instant.now(),
                false
        );

        when(preferencesService.updatePreferences(eq(userId), any(UpdateNotificationPreferencesRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/notification-preferences")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(false))
                .andExpect(jsonPath("$.reminderEmailsEnabled").value(true))
                .andExpect(jsonPath("$.dailyDigestEnabled").value(false))
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.pushNotificationsEnabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/notification-preferences: 400 on invalid blank timezone")
    void testUpdatePreferencesInvalidTimezone() throws Exception {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                true,
                true,
                true,
                LocalTime.of(8, 0),
                "" // blank
        );

        mockMvc.perform(put("/api/notification-preferences")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/notification-preferences: 401 without Bearer token")
    void testUpdatePreferencesUnauthenticated() throws Exception {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                true,
                true,
                true,
                LocalTime.of(8, 0),
                "UTC",
                true
        );

        mockMvc.perform(put("/api/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/notification-preferences: ignores client-supplied userId and strictly binds to authenticated principal")
    void testUpdatePreferencesIgnoresClientSuppliedUserId() throws Exception {
        UUID maliciousUserId = UUID.randomUUID();
        String jsonWithInjectedUserId = """
                {
                    "userId": "%s",
                    "emailNotificationsEnabled": true,
                    "reminderEmailsEnabled": true,
                    "dailyDigestEnabled": true,
                    "dailyDigestTime": "08:00:00",
                    "timezone": "UTC",
                    "pushNotificationsEnabled": true
                }
                """.formatted(maliciousUserId);

        when(preferencesService.updatePreferences(eq(userId), any(UpdateNotificationPreferencesRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(put("/api/notification-preferences")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithInjectedUserId))
                .andExpect(status().isOk());

        // Verifies the service was invoked with authenticated userId, NOT the injected maliciousUserId
        verify(preferencesService).updatePreferences(eq(userId), any(UpdateNotificationPreferencesRequest.class));
    }

    @Test
    @DisplayName("POST /api/notification-preferences/test-email: triggers test reminder email for authenticated user")
    void testSendTestReminderEmail() throws Exception {
        mockMvc.perform(post("/api/notification-preferences/test-email")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test reminder email sent successfully"));

        verify(preferencesService).sendTestReminderEmail(userId);
    }

    @Test
    @DisplayName("POST /api/notification-preferences/test-digest: triggers test daily digest for authenticated user")
    void testSendTestDailyDigest() throws Exception {
        mockMvc.perform(post("/api/notification-preferences/test-digest")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Test daily digest email sent successfully"));

        verify(preferencesService).sendTestDailyDigest(userId);
    }
}
