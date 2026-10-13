package com.lifeinbox.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.PushSubscriptionDeleteRequest;
import com.lifeinbox.dto.PushSubscriptionRequest;
import com.lifeinbox.dto.RegisterRequest;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.entity.PushSubscription;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.mail.EmailProvider;
import com.lifeinbox.push.WebPushClient;
import com.lifeinbox.repository.PushSubscriptionRepository;
import com.lifeinbox.scheduler.ReminderEmailScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "lifeinbox.push.enabled=true",
        "lifeinbox.push.vapid-public-key=BEl62iUYgUivxIkv69yViEuiBIa-M9-26GoSlTk2PRtv5bVn5GCuh6h3BQ9gkUidrgrgWv9T2VOyzU02079675g",
        "lifeinbox.push.vapid-private-key=s3cr3tPr1v4t3K3yTh4tMustN3v3rB3L0gg3d0rExpos3d",
        "lifeinbox.push.vapid-subject=mailto:admin@lifeinbox.local",
        "lifeinbox.push.retry.enabled=false"
})
class WebPushIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private ReminderEmailScheduler reminderEmailScheduler;

    @MockBean
    private WebPushClient webPushClient;

    @MockBean
    private EmailProvider emailProvider;

    @Test
    @DisplayName("Complete end-to-end Web Push flow: Public Key -> Register -> Anti-Hijack -> Preferences -> Due Reminder -> Scheduler Dispatch -> Deduplication -> Unsubscribe")
    void testCompleteWebPushLifecycle() throws Exception {
        // Setup mock push transport
        when(webPushClient.send(any(PushSubscription.class), anyString())).thenReturn(201);

        // 1. GET /api/push/public-key (public endpoint)
        mockMvc.perform(get("/api/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BEl62iUYgUivxIkv69yViEuiBIa-M9-26GoSlTk2PRtv5bVn5GCuh6h3BQ9gkUidrgrgWv9T2VOyzU02079675g"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.privateKey").doesNotExist());

        // 2. Register User 1
        String user1Email = "push.user1." + UUID.randomUUID() + "@example.com";
        RegisterRequest registerUser1 = new RegisterRequest("User One", user1Email, "Password123!");
        MvcResult regResult1 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUser1)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode regNode1 = objectMapper.readTree(regResult1.getResponse().getContentAsString());
        String token1 = regNode1.get("accessToken").asText();
        UUID user1Id = UUID.fromString(regNode1.get("user").get("id").asText());

        // 3. Register User 2 (for cross-user isolation verification)
        String user2Email = "push.user2." + UUID.randomUUID() + "@example.com";
        RegisterRequest registerUser2 = new RegisterRequest("User Two", user2Email, "Password123!");
        MvcResult regResult2 = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerUser2)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode regNode2 = objectMapper.readTree(regResult2.getResponse().getContentAsString());
        String token2 = regNode2.get("accessToken").asText();
        UUID user2Id = UUID.fromString(regNode2.get("user").get("id").asText());

        // 4. User 1 registers a browser push subscription
        String testEndpoint = "https://fcm.googleapis.com/fcm/send/integration-token-" + UUID.randomUUID();
        PushSubscriptionRequest subRequest = new PushSubscriptionRequest(
                testEndpoint,
                new PushSubscriptionRequest.Keys("p256dh-integration-key", "auth-integration-secret")
        );

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(subRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value(testEndpoint))
                .andExpect(jsonPath("$.active").value(true));

        // Verify persisted in database
        List<PushSubscription> user1Subs = pushSubscriptionRepository.findAllByUserIdAndActiveTrue(user1Id);
        assertThat(user1Subs).hasSize(1);
        assertThat(user1Subs.get(0).getEndpoint()).isEqualTo(testEndpoint);

        // 5. Anti-hijacking: User 2 attempts to register User 1's endpoint -> rejected with 409 Conflict
        PushSubscriptionRequest hijackRequest = new PushSubscriptionRequest(
                testEndpoint,
                new PushSubscriptionRequest.Keys("p256dh-hijacker", "auth-hijacker")
        );
        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hijackRequest)))
                .andExpect(status().isConflict());

        // Verify User 1 still owns the endpoint
        PushSubscription verifiedSub = pushSubscriptionRepository.findByEndpoint(testEndpoint).orElseThrow();
        assertThat(verifiedSub.getUser().getId()).isEqualTo(user1Id);

        // 6. User 1 re-registers same endpoint with new keys -> reactivates/updates
        PushSubscriptionRequest updateRequest = new PushSubscriptionRequest(
                testEndpoint,
                new PushSubscriptionRequest.Keys("p256dh-updated-key", "auth-updated-secret")
        );
        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        PushSubscription updatedSub = pushSubscriptionRepository.findByEndpoint(testEndpoint).orElseThrow();
        assertThat(updatedSub.getP256dh()).isEqualTo("p256dh-updated-key");

        // 7. Notification Preferences: verify and toggle
        mockMvc.perform(get("/api/notification-preferences")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationsEnabled").value(true));

        // 8. Create a reminder due in the past for User 1
        CreateReminderRequest createReminder = new CreateReminderRequest(
                "Pick up dry cleaning",
                "Don't forget the jacket",
                Instant.now().minus(5, ChronoUnit.MINUTES),
                ReminderPriority.HIGH,
                "Personal"
        );

        MvcResult reminderResult = mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReminder)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode reminderNode = objectMapper.readTree(reminderResult.getResponse().getContentAsString());
        String reminderId = reminderNode.get("id").asText();

        // 9. Execute reminder scheduler: verifies push delivery via WebPushClient
        reminderEmailScheduler.processDueReminders();

        ArgumentCaptor<PushSubscription> subCaptor = ArgumentCaptor.forClass(PushSubscription.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webPushClient, times(1)).send(subCaptor.capture(), payloadCaptor.capture());

        assertThat(subCaptor.getValue().getEndpoint()).isEqualTo(testEndpoint);
        String payloadJson = payloadCaptor.getValue();
        assertThat(payloadJson).contains("\"title\":\"Reminder Due\"");
        assertThat(payloadJson).contains("\"body\":\"Pick up dry cleaning\"");
        assertThat(payloadJson).contains("\"reminderId\":\"" + reminderId + "\"");
        assertThat(payloadJson).contains("\"route\":\"/dashboard\"");
        assertThat(payloadJson).contains("\"type\":\"REMINDER_DUE\"");
        assertThat(payloadJson).contains("\"tag\":\"reminder-" + reminderId + "\"");

        // 10. Deduplication: running scheduler again within deduplication window does not re-send push
        reminderEmailScheduler.processDueReminders();
        verify(webPushClient, times(1)).send(any(PushSubscription.class), anyString());

        // 11. Cross-user unsubscribe safety: User 2 attempts to unsubscribe User 1's endpoint
        PushSubscriptionDeleteRequest user2DeleteAttempt = new PushSubscriptionDeleteRequest(testEndpoint);
        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user2DeleteAttempt)))
                .andExpect(status().isNoContent());

        // User 1's subscription should still be active
        PushSubscription stillActiveSub = pushSubscriptionRepository.findByEndpoint(testEndpoint).orElseThrow();
        assertThat(stillActiveSub.isActive()).isTrue();

        // 12. User 1 unsubscribes own endpoint
        PushSubscriptionDeleteRequest user1Delete = new PushSubscriptionDeleteRequest(testEndpoint);
        mockMvc.perform(delete("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1Delete)))
                .andExpect(status().isNoContent());

        // Subscription should now be inactive
        PushSubscription deactivatedSub = pushSubscriptionRepository.findByEndpoint(testEndpoint).orElseThrow();
        assertThat(deactivatedSub.isActive()).isFalse();
        assertThat(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(user1Id)).isEmpty();
    }
}
