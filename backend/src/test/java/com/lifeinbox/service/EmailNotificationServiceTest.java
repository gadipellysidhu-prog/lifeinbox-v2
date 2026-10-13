package com.lifeinbox.service;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.NotificationType;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.mail.EmailProvider;
import com.lifeinbox.mail.EmailTemplateService;
import com.lifeinbox.repository.EmailDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private EmailProvider emailProvider;

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    private LifeInboxProperties properties;
    private EmailTemplateService emailTemplateService;
    private EmailNotificationService notificationService;

    private User sampleUser;
    private Reminder sampleReminder;

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        properties.setAppBaseUrl("http://localhost:5173");
        emailTemplateService = new EmailTemplateService(properties);

        notificationService = new EmailNotificationService(
                emailProvider,
                emailTemplateService,
                emailDeliveryRepository,
                properties
        );

        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Emma")
                .email("emma@example.com")
                .build();

        sampleReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .title("Renew Passport")
                .dueAt(Instant.parse("2026-10-10T12:00:00Z"))
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("sendReminderNotification successfully creates delivery and dispatches email")
    void testSendReminderNotificationSuccess() throws Exception {
        String dedupKey = "REMINDER:" + sampleReminder.getId() + ":" + sampleReminder.getDueAt().toEpochMilli();
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.empty());
        when(emailDeliveryRepository.saveAndFlush(any(EmailDelivery.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = notificationService.sendReminderNotification(
                sampleReminder, sampleUser, ZoneId.of("Europe/London"));

        assertThat(result).isTrue();
        verify(emailProvider).sendEmail(any(EmailMessage.class));

        ArgumentCaptor<EmailDelivery> captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(emailDeliveryRepository).save(captor.capture());
        EmailDelivery saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("sendReminderNotification skips dispatch when deduplication key was already sent")
    void testSendReminderNotificationSkipsDuplicate() throws Exception {
        String dedupKey = "REMINDER:" + sampleReminder.getId() + ":" + sampleReminder.getDueAt().toEpochMilli();
        EmailDelivery alreadySent = EmailDelivery.builder()
                .deduplicationKey(dedupKey)
                .status(EmailDeliveryStatus.SENT)
                .build();
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.of(alreadySent));

        boolean result = notificationService.sendReminderNotification(
                sampleReminder, sampleUser, ZoneId.of("Europe/London"));

        assertThat(result).isFalse();
        verify(emailProvider, never()).sendEmail(any(EmailMessage.class));
    }

    @Test
    @DisplayName("sendReminderNotification records failure status when EmailProvider throws exception")
    void testSendReminderNotificationFailureRecorded() throws Exception {
        String dedupKey = "REMINDER:" + sampleReminder.getId() + ":" + sampleReminder.getDueAt().toEpochMilli();
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.empty());
        when(emailDeliveryRepository.saveAndFlush(any(EmailDelivery.class))).thenAnswer(i -> i.getArgument(0));

        doThrow(new RuntimeException("SMTP connection timed out")).when(emailProvider).sendEmail(any(EmailMessage.class));

        boolean result = notificationService.sendReminderNotification(
                sampleReminder, sampleUser, ZoneId.of("UTC"));

        assertThat(result).isFalse();

        ArgumentCaptor<EmailDelivery> captor = ArgumentCaptor.forClass(EmailDelivery.class);
        verify(emailDeliveryRepository).save(captor.capture());
        EmailDelivery saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("SMTP connection timed out");
        assertThat(saved.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("sendDailyDigestNotification sends email and enforces date-level idempotency")
    void testSendDailyDigestNotification() throws Exception {
        String dedupKey = "DAILY_DIGEST:" + sampleUser.getId() + ":2026-10-10";
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.empty());
        when(emailDeliveryRepository.saveAndFlush(any(EmailDelivery.class))).thenAnswer(i -> i.getArgument(0));

        boolean result = notificationService.sendDailyDigestNotification(
                sampleUser,
                ZoneId.of("UTC"),
                "2026-10-10",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(result).isTrue();
        verify(emailProvider).sendEmail(any(EmailMessage.class));

        // Attempting again with existing SENT delivery should return false
        EmailDelivery sentDelivery = EmailDelivery.builder()
                .deduplicationKey(dedupKey)
                .status(EmailDeliveryStatus.SENT)
                .build();
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.of(sentDelivery));

        boolean duplicateResult = notificationService.sendDailyDigestNotification(
                sampleUser,
                ZoneId.of("UTC"),
                "2026-10-10",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        assertThat(duplicateResult).isFalse();
    }

    @Test
    @DisplayName("retryDelivery sends email and marks delivery as SENT")
    void testRetryDeliverySuccess() throws Exception {
        EmailDelivery failedDelivery = EmailDelivery.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .reminder(sampleReminder)
                .notificationType(NotificationType.REMINDER)
                .recipientEmail(sampleUser.getEmail())
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .build();

        boolean result = notificationService.retryDelivery(failedDelivery, ZoneId.of("UTC"));

        assertThat(result).isTrue();
        verify(emailProvider).sendEmail(any(EmailMessage.class));
        assertThat(failedDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(failedDelivery.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("retryDelivery cancels and marks failed if associated reminder was completed or deleted")
    void testRetryDeliveryAbortsIfReminderCompletedOrDeleted() throws Exception {
        // Reminder completed
        sampleReminder.setStatus(ReminderStatus.COMPLETED);
        EmailDelivery failedDelivery = EmailDelivery.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .reminder(sampleReminder)
                .notificationType(NotificationType.REMINDER)
                .recipientEmail(sampleUser.getEmail())
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .build();

        boolean resultCompleted = notificationService.retryDelivery(failedDelivery, ZoneId.of("UTC"));
        assertThat(resultCompleted).isFalse();
        assertThat(failedDelivery.getFailureReason()).contains("Reminder was deleted or completed before retry");

        // Reminder deleted (reminder == null)
        EmailDelivery deletedReminderDelivery = EmailDelivery.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .reminder(null)
                .notificationType(NotificationType.REMINDER)
                .recipientEmail(sampleUser.getEmail())
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .build();

        boolean resultDeleted = notificationService.retryDelivery(deletedReminderDelivery, ZoneId.of("UTC"));
        assertThat(resultDeleted).isFalse();
        assertThat(deletedReminderDelivery.getFailureReason()).contains("Reminder was deleted or completed before retry");
    }
}
