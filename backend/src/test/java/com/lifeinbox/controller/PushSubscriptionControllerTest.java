package com.lifeinbox.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.SecurityConfig;
import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.dto.PushSubscriptionDeleteRequest;
import com.lifeinbox.dto.PushSubscriptionRequest;
import com.lifeinbox.dto.PushSubscriptionResponse;
import com.lifeinbox.dto.VapidPublicKeyResponse;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.DuplicateResourceException;
import com.lifeinbox.security.CustomUserDetailsService;
import com.lifeinbox.security.JwtAuthenticationFilter;
import com.lifeinbox.security.JwtProperties;
import com.lifeinbox.security.JwtService;
import com.lifeinbox.security.RestAccessDeniedHandler;
import com.lifeinbox.security.RestAuthenticationEntryPoint;
import com.lifeinbox.security.UserPrincipal;
import com.lifeinbox.service.PushNotificationService;
import com.lifeinbox.service.PushSubscriptionService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushSubscriptionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, JwtProperties.class})
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PushSubscriptionService pushSubscriptionService;

    @MockBean
    private PushNotificationService pushNotificationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    private UUID userId;
    private UserPrincipal samplePrincipal;
    private PushSubscriptionRequest validRequest;
    private PushSubscriptionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("pwd")
                .build();

        samplePrincipal = UserPrincipal.create(user);

        validRequest = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/sample-token",
                new PushSubscriptionRequest.Keys("sample-p256dh", "sample-auth")
        );

        sampleResponse = new PushSubscriptionResponse(
                UUID.randomUUID(),
                "https://fcm.googleapis.com/fcm/send/sample-token",
                true,
                Instant.now(),
                Instant.now()
        );

        when(jwtService.validateToken("valid.token")).thenReturn(true);
        when(jwtService.extractUserId("valid.token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(samplePrincipal);
    }

    @Test
    @DisplayName("GET /api/push/public-key returns 200 with public key and enabled status")
    void testGetPublicKeySuccess() throws Exception {
        when(pushSubscriptionService.getPublicKey()).thenReturn(
                new VapidPublicKeyResponse("BNcR...valid-public-key", true)
        );

        mockMvc.perform(get("/api/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BNcR...valid-public-key"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects unauthenticated request with 401")
    void testRegisterUnauthenticatedRejection() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/push/subscriptions rejects unauthenticated request with 401")
    void testUnsubscribeUnauthenticatedRejection() throws Exception {
        mockMvc.perform(delete("/api/push/subscriptions")
                        .param("endpoint", "https://push.example.com/test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions returns 200 with subscription response when authenticated")
    void testRegisterSuccess() throws Exception {
        when(pushSubscriptionService.registerSubscription(eq(userId), any(PushSubscriptionRequest.class)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sampleResponse.id().toString()))
                .andExpect(jsonPath("$.endpoint").value(sampleResponse.endpoint()))
                .andExpect(jsonPath("$.active").value(true));

        verify(pushSubscriptionService).registerSubscription(eq(userId), any(PushSubscriptionRequest.class));
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects missing or blank endpoint with 400")
    void testRegisterMissingEndpoint() throws Exception {
        PushSubscriptionRequest invalidRequest = new PushSubscriptionRequest(
                "",
                new PushSubscriptionRequest.Keys("sample-p256dh", "sample-auth")
        );

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endpoint").exists());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects missing keys with 400")
    void testRegisterMissingKeys() throws Exception {
        String invalidJson = """
                {
                    "endpoint": "https://push.example.com/endpoint"
                }
                """;

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keys").exists());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects blank keys.p256dh or keys.auth with 400")
    void testRegisterBlankKeyFields() throws Exception {
        PushSubscriptionRequest invalidRequest = new PushSubscriptionRequest(
                "https://push.example.com/endpoint",
                new PushSubscriptionRequest.Keys("", "   ")
        );

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions returns 409 Conflict when endpoint is owned by another user")
    void testRegisterConflictWhenEndpointOwnedByAnotherUser() throws Exception {
        when(pushSubscriptionService.registerSubscription(eq(userId), any(PushSubscriptionRequest.class)))
                .thenThrow(new DuplicateResourceException("Push subscription endpoint is already registered by another account"));

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Push subscription endpoint is already registered by another account"));
    }

    @Test
    @DisplayName("DELETE /api/push/subscriptions returns 204 No Content when using request body")
    void testUnsubscribeWithRequestBody() throws Exception {
        PushSubscriptionDeleteRequest deleteRequest = new PushSubscriptionDeleteRequest(
                "https://fcm.googleapis.com/fcm/send/sample-token"
        );

        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unsubscribe(eq(userId), eq("https://fcm.googleapis.com/fcm/send/sample-token"));
    }

    @Test
    @DisplayName("DELETE /api/push/subscriptions returns 204 No Content when using query parameter")
    void testUnsubscribeWithQueryParam() throws Exception {
        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .param("endpoint", "https://fcm.googleapis.com/fcm/send/sample-token"))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unsubscribe(eq(userId), eq("https://fcm.googleapis.com/fcm/send/sample-token"));
    }

    @Test
    @DisplayName("DELETE /api/push/subscriptions returns 400 Bad Request when endpoint is absent")
    void testUnsubscribeMissingEndpoint() throws Exception {
        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects dangerous or invalid endpoint schemes (javascript:, data:, file:, ftp:, //, /\\, \\\\)")
    void testRegisterInvalidEndpointSchemeRejected() throws Exception {
        List<String> dangerousEndpoints = List.of(
                "javascript:alert(1)",
                "javascript://evil.com",
                "data:text/html,<script>alert(1)</script>",
                "data:text/plain,malicious",
                "file:///etc/passwd",
                "ftp://evil.com/payload",
                "//evil.example/push",
                "/\\evil.example",
                "\\\\evil.example"
        );

        for (String badEndpoint : dangerousEndpoints) {
            PushSubscriptionRequest request = new PushSubscriptionRequest(
                    badEndpoint,
                    new PushSubscriptionRequest.Keys("sample-p256dh", "sample-auth")
            );

            mockMvc.perform(post("/api/push/subscriptions")
                            .header("Authorization", "Bearer valid.token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.endpoint").exists());
        }
    }

    @Test
    @DisplayName("POST /api/push/subscriptions returns 409 Conflict when database integrity violation occurs during race condition")
    void testRegisterDataIntegrityViolationReturnsConflict() throws Exception {
        when(pushSubscriptionService.registerSubscription(eq(userId), any(PushSubscriptionRequest.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource conflict"))
                .andExpect(jsonPath("$.detail").value("A data integrity conflict occurred. Please verify your request data."));
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects oversized endpoint (>2048 chars)")
    void testRegisterOversizedEndpointRejected() throws Exception {
        String longEndpoint = "https://fcm.googleapis.com/" + "a".repeat(2040);
        PushSubscriptionRequest oversizedRequest = new PushSubscriptionRequest(
                longEndpoint,
                new PushSubscriptionRequest.Keys("sample-p256dh", "sample-auth")
        );

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endpoint").exists());
    }

    @Test
    @DisplayName("POST /api/push/subscriptions rejects oversized key fields (>255 chars)")
    void testRegisterOversizedKeyFieldsRejected() throws Exception {
        PushSubscriptionRequest oversizedKeysRequest = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/sample-token",
                new PushSubscriptionRequest.Keys("k".repeat(256), "sample-auth")
        );

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oversizedKeysRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/push/subscriptions rejects invalid endpoint schemes")
    void testUnsubscribeInvalidEndpointSchemeRejected() throws Exception {
        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer valid.token")
                        .param("endpoint", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/push/public-key never exposes private key or server secrets")
    void testGetPublicKeyNeverExposesPrivateKey() throws Exception {
        when(pushSubscriptionService.getPublicKey()).thenReturn(
                new VapidPublicKeyResponse("BNcR...valid-public-key", true)
        );

        mockMvc.perform(get("/api/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BNcR...valid-public-key"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.privateKey").doesNotExist())
                .andExpect(jsonPath("$.vapidPrivateKey").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/push/test dispatches test notification for authenticated user")
    void testSendTestNotification() throws Exception {
        when(pushNotificationService.sendToUser(eq(userId), any(PushNotification.class)))
                .thenReturn(new PushDispatchResult(1, 1, 0, 0, 0));

        mockMvc.perform(post("/api/push/test")
                        .header("Authorization", "Bearer valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempted").value(1))
                .andExpect(jsonPath("$.successful").value(1));
    }
}
