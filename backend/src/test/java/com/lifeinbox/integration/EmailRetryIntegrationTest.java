package com.lifeinbox.integration;

import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.NotificationType;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.mail.EmailProvider;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.repository.UserRepository;
import com.lifeinbox.scheduler.EmailRetryScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class EmailRetryIntegrationTest {

    @MockBean
    private EmailProvider emailProvider;

    @Autowired
    private EmailRetryScheduler emailRetryScheduler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private EmailDeliveryRepository emailDeliveryRepository;

    @Autowired
    private UserNotificationPreferencesRepository preferencesRepository;

    @Test
    @DisplayName("Email retry scheduler successfully processes detached failed reminder delivery without LazyInitializationException")
    void testRetryFailedReminderDeliveryWithoutLazyInitializationException() throws Exception {
        User user = userRepository.save(User.builder()
                .name("Retry Test User")
                .email("retry." + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed-password")
                .build());

        preferencesRepository.save(UserNotificationPreferences.builder()
                .user(user)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(true)
                .timezone("UTC")
                .build());

        Reminder reminder = reminderRepository.save(Reminder.builder()
                .user(user)
                .title("Dentist Appointment")
                .notes("Routine checkup")
                .dueAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build());

        EmailDelivery failedDelivery = emailDeliveryRepository.save(EmailDelivery.builder()
                .user(user)
                .reminder(reminder)
                .notificationType(NotificationType.REMINDER)
                .deduplicationKey("REMINDER:" + reminder.getId() + ":" + reminder.getDueAt().toEpochMilli())
                .recipientEmail(user.getEmail())
                .subject("LifeInbox Reminder: Dentist Appointment")
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(Instant.now().minus(120, ChronoUnit.SECONDS))
                .failureReason("Initial SMTP network timeout")
                .build());

        // Use saveAndFlush so everything is committed to H2
        // Because the test is non-transactional, there is no shared Hibernate session between repository calls
        emailDeliveryRepository.saveAndFlush(failedDelivery);

        // Trigger the retry scheduler
        emailRetryScheduler.processFailedRetries();

        // Verify delivery was processed to SENT without throwing LazyInitializationException
        EmailDelivery updatedDelivery = emailDeliveryRepository.findById(failedDelivery.getId()).orElseThrow();
        assertThat(updatedDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(updatedDelivery.getAttemptCount()).isEqualTo(2);
        assertThat(updatedDelivery.getSentAt()).isNotNull();
        assertThat(updatedDelivery.getFailureReason()).isNull();

        verify(emailProvider, atLeastOnce()).sendEmail(any(EmailMessage.class));
    }

    @Test
    @DisplayName("Email retry scheduler cancels retry when reminder was completed prior to retry")
    void testRetryCancelsWhenReminderIsCompleted() {
        User user = userRepository.save(User.builder()
                .name("Completed Test User")
                .email("completed." + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed-password")
                .build());

        Reminder reminder = reminderRepository.save(Reminder.builder()
                .user(user)
                .title("Completed Task")
                .notes("Finished early")
                .dueAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .priority(ReminderPriority.MEDIUM)
                .status(ReminderStatus.COMPLETED)
                .completedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build());

        EmailDelivery failedDelivery = emailDeliveryRepository.save(EmailDelivery.builder()
                .user(user)
                .reminder(reminder)
                .notificationType(NotificationType.REMINDER)
                .deduplicationKey("REMINDER:" + reminder.getId() + ":" + reminder.getDueAt().toEpochMilli())
                .recipientEmail(user.getEmail())
                .subject("LifeInbox Reminder: Completed Task")
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(Instant.now().minus(120, ChronoUnit.SECONDS))
                .failureReason("Initial failure")
                .build());

        emailDeliveryRepository.saveAndFlush(failedDelivery);

        emailRetryScheduler.processFailedRetries();

        EmailDelivery updatedDelivery = emailDeliveryRepository.findById(failedDelivery.getId()).orElseThrow();
        assertThat(updatedDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(updatedDelivery.getFailureReason()).contains("Reminder was deleted or completed before retry");
    }

    @Test
    @DisplayName("Email retry scheduler successfully processes digest delivery where reminder is null")
    void testRetryDigestDeliveryWithNullReminder() throws Exception {
        User user = userRepository.saveAndFlush(User.builder()
                .name("Digest Test User")
                .email("digest." + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed-password")
                .build());

        EmailDelivery failedDelivery = emailDeliveryRepository.saveAndFlush(EmailDelivery.builder()
                .user(user)
                .reminder(null)
                .notificationType(NotificationType.DAILY_DIGEST)
                .deduplicationKey("DAILY_DIGEST:" + user.getId() + ":2026-10-12")
                .recipientEmail(user.getEmail())
                .subject("LifeInbox — Your Daily Plan")
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(Instant.now().minus(120, ChronoUnit.SECONDS))
                .failureReason("Initial timeout")
                .build());

        emailRetryScheduler.processFailedRetries();

        EmailDelivery updatedDelivery = emailDeliveryRepository.findById(failedDelivery.getId()).orElseThrow();
        assertThat(updatedDelivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(updatedDelivery.getAttemptCount()).isEqualTo(2);
        assertThat(updatedDelivery.getSentAt()).isNotNull();

        verify(emailProvider, atLeastOnce()).sendEmail(any(EmailMessage.class));
    }
}
