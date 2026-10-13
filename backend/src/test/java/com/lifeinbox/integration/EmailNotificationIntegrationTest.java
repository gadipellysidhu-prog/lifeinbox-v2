package com.lifeinbox.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.dto.RegisterRequest;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.mail.EmailProvider;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.scheduler.DailyDigestScheduler;
import com.lifeinbox.scheduler.ReminderEmailScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EmailProvider emailProvider;

    @Autowired
    private ReminderEmailScheduler reminderEmailScheduler;

    @Autowired
    private DailyDigestScheduler dailyDigestScheduler;

    @Autowired
    private EmailDeliveryRepository emailDeliveryRepository;

    @Test
    @DisplayName("Complete end-to-end integration: User Registration, Preference Setup, Due Reminder Notification, Daily Digest, and Persistent Idempotency")
    void testEmailNotificationEndToEndLifecycle() throws Exception {
        // 1. Register a new user
        RegisterRequest registerReq = new RegisterRequest("Test User", "test.user@example.com", "Password123!");
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode regNode = objectMapper.readTree(regResult.getResponse().getContentAsString());
        String token = regNode.get("accessToken").asText();

        // 2. Verify notification preferences are automatically created
        mockMvc.perform(get("/api/notification-preferences")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.reminderEmailsEnabled").value(true))
                .andExpect(jsonPath("$.dailyDigestEnabled").value(true));

        // 3. Update preferences with specific timezone and morning digest time
        UpdateNotificationPreferencesRequest updatePrefsReq = new UpdateNotificationPreferencesRequest(
                true,
                true,
                true,
                LocalTime.of(0, 0), // 00:00 local time so it's guaranteed to be reached today
                "UTC"
        );
        mockMvc.perform(put("/api/notification-preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatePrefsReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.dailyDigestTime").value("00:00:00"));

        // 4. Create a due reminder in the past (eligible for immediate alert)
        Instant pastDue = Instant.now().minus(5, ChronoUnit.MINUTES);
        CreateReminderRequest createReminderReq = new CreateReminderRequest(
                "Urgent Bill Payment",
                "Pay electricity bill online before deadline",
                pastDue,
                ReminderPriority.HIGH,
                "Utilities"
        );
        mockMvc.perform(post("/api/reminders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReminderReq)))
                .andExpect(status().isCreated());

        // 5. Run Reminder Scheduler
        reminderEmailScheduler.processDueReminders();

        // 6. Verify EmailProvider was invoked and delivery was stored as SENT
        verify(emailProvider, atLeastOnce()).sendEmail(any(EmailMessage.class));

        List<EmailDelivery> deliveries = emailDeliveryRepository.findAll();
        assertThat(deliveries).isNotEmpty();

        EmailDelivery reminderDelivery = deliveries.stream()
                .filter(d -> d.getSubject().contains("Urgent Bill Payment"))
                .findFirst()
                .orElse(null);

        assertThat(reminderDelivery).isNotNull();
        assertThat(reminderDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(reminderDelivery.getRecipientEmail()).isEqualTo("test.user@example.com");
        assertThat(reminderDelivery.getDeduplicationKey()).startsWith("REMINDER:");

        // 7. Verify Idempotency: Triggering scheduler again must NOT send duplicate email
        long deliveriesCountBefore = emailDeliveryRepository.count();
        reminderEmailScheduler.processDueReminders();
        long deliveriesCountAfter = emailDeliveryRepository.count();

        assertThat(deliveriesCountAfter).isEqualTo(deliveriesCountBefore);

        // 8. Run Daily Digest Scheduler
        dailyDigestScheduler.processDailyDigests();

        // Verify Daily Digest delivery record created
        List<EmailDelivery> digestDeliveries = emailDeliveryRepository.findAll().stream()
                .filter(d -> d.getDeduplicationKey().startsWith("DAILY_DIGEST:"))
                .toList();
        assertThat(digestDeliveries).hasSize(1);
        EmailDelivery digestDelivery = digestDeliveries.get(0);
        assertThat(digestDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(digestDelivery.getSubject()).isEqualTo("LifeInbox — Your Daily Plan");

        // 9. Run Daily Digest Scheduler again and verify no duplicate digest is sent today
        dailyDigestScheduler.processDailyDigests();
        List<EmailDelivery> digestDeliveriesAfter = emailDeliveryRepository.findAll().stream()
                .filter(d -> d.getDeduplicationKey().startsWith("DAILY_DIGEST:"))
                .toList();
        assertThat(digestDeliveriesAfter).hasSize(1);
    }
}
