package com.lifeinbox.scheduler;

import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.service.EmailNotificationService;
import com.lifeinbox.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderEmailSchedulerTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private ReminderEmailScheduler scheduler;

    private User sampleUser;
    private Reminder sampleReminder;

    @BeforeEach
    void setUp() {
        scheduler.clearProcessedPushKeys();

        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Grace")
                .email("grace@example.com")
                .build();

        sampleReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .title("Doctor Appointment")
                .dueAt(Instant.now().minusSeconds(10))
                .priority(ReminderPriority.HIGH)
                .status(ReminderStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("processDueReminders finds due reminders, sends email, and dispatches push notification with valid payload")
    void testProcessDueRemindersSuccess() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(true)
                .timezone("Asia/Kolkata")
                .build();
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.of(prefs));
        when(emailNotificationService.sendReminderNotification(eq(sampleReminder), eq(sampleUser), eq(ZoneId.of("Asia/Kolkata"))))
                .thenReturn(true);
        when(pushNotificationService.sendToUser(eq(sampleUser.getId()), any(PushNotification.class)))
                .thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        verify(emailNotificationService).sendReminderNotification(sampleReminder, sampleUser, ZoneId.of("Asia/Kolkata"));

        ArgumentCaptor<PushNotification> pushCaptor = ArgumentCaptor.forClass(PushNotification.class);
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), pushCaptor.capture());

        PushNotification push = pushCaptor.getValue();
        assertThat(push.title()).isEqualTo("Reminder Due");
        assertThat(push.body()).isEqualTo("Doctor Appointment");
        assertThat(push.data()).isNotNull();
        assertThat(push.data().get("type")).isEqualTo("REMINDER_DUE");
        assertThat(push.data().get("reminderId")).isEqualTo(sampleReminder.getId().toString());
        assertThat(push.data().get("route")).isEqualTo("/dashboard");
        assertThat(push.data().get("tag")).isEqualTo("reminder-" + sampleReminder.getId());
    }

    @Test
    @DisplayName("processDueReminders falls back to generic body if reminder title is blank")
    void testProcessDueRemindersBlankTitleFallback() {
        Reminder blankTitleReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .title("   ")
                .dueAt(Instant.now().minusSeconds(10))
                .priority(ReminderPriority.MEDIUM)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(blankTitleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(pushNotificationService.sendToUser(eq(sampleUser.getId()), any(PushNotification.class)))
                .thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        ArgumentCaptor<PushNotification> pushCaptor = ArgumentCaptor.forClass(PushNotification.class);
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), pushCaptor.capture());
        assertThat(pushCaptor.getValue().body()).isEqualTo("You have a reminder due");
    }

    @Test
    @DisplayName("processDueReminders skips email when user has email notifications disabled, but still dispatches push")
    void testProcessDueRemindersDisabledUser() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(false) // email disabled
                .reminderEmailsEnabled(true)
                .timezone("UTC")
                .build();
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.of(prefs));
        when(pushNotificationService.sendToUser(eq(sampleUser.getId()), any(PushNotification.class)))
                .thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        verify(emailNotificationService, never()).sendReminderNotification(any(), any(), any());
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), any(PushNotification.class));
    }

    @Test
    @DisplayName("processDueReminders skips email when reminderEmailsEnabled is disabled, but still dispatches push")
    void testProcessDueRemindersReminderEmailsDisabled() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(false) // specific toggle disabled
                .timezone("UTC")
                .build();
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.of(prefs));
        when(pushNotificationService.sendToUser(eq(sampleUser.getId()), any(PushNotification.class)))
                .thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        verify(emailNotificationService, never()).sendReminderNotification(any(), any(), any());
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), any(PushNotification.class));
    }

    @Test
    @DisplayName("processDueReminders skips push when user has pushNotificationsEnabled disabled, but still sends email")
    void testProcessDueRemindersPushPreferenceDisabled() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(true)
                .pushNotificationsEnabled(false) // push disabled
                .timezone("UTC")
                .build();
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.of(prefs));
        when(emailNotificationService.sendReminderNotification(eq(sampleReminder), eq(sampleUser), any(ZoneId.class)))
                .thenReturn(true);

        scheduler.processDueReminders();

        verify(emailNotificationService).sendReminderNotification(eq(sampleReminder), eq(sampleUser), any(ZoneId.class));
        verify(pushNotificationService, never()).sendToUser(any(), any());
    }

    @Test
    @DisplayName("processDueReminders enforces user isolation across different users")
    void testProcessDueRemindersUserIsolation() {
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .name("Alex")
                .email("alex@example.com")
                .build();

        Reminder otherReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(otherUser)
                .title("Submit Expense Report")
                .dueAt(Instant.now().minusSeconds(10))
                .priority(ReminderPriority.LOW)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder, otherReminder));
        when(preferencesRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        verify(pushNotificationService, times(1)).sendToUser(eq(sampleUser.getId()), any());
        verify(pushNotificationService, times(1)).sendToUser(eq(otherUser.getId()), any());
    }

    @Test
    @DisplayName("Push failure does not prevent email delivery")
    void testPushFailureDoesNotPreventEmailDelivery() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(emailNotificationService.sendReminderNotification(any(), any(), any())).thenReturn(true);
        when(pushNotificationService.sendToUser(any(), any())).thenThrow(new RuntimeException("Push provider timeout"));

        scheduler.processDueReminders();

        verify(emailNotificationService).sendReminderNotification(eq(sampleReminder), eq(sampleUser), any());
    }

    @Test
    @DisplayName("Email failure does not prevent push notification dispatch")
    void testEmailFailureDoesNotPreventPushNotification() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(emailNotificationService.sendReminderNotification(any(), any(), any()))
                .thenThrow(new RuntimeException("SMTP connection refused"));
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(new PushDispatchResult(1, 1, 0));

        scheduler.processDueReminders();

        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), any());
    }

    @Test
    @DisplayName("Push disabled or no subscriptions allows scheduler to complete normally")
    void testPushDisabledOrNoSubscriptionsCompletesNormally() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(new PushDispatchResult(0, 0, 0));

        scheduler.processDueReminders();

        verify(emailNotificationService).sendReminderNotification(eq(sampleReminder), eq(sampleUser), any());
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), any());
    }

    @Test
    @DisplayName("Deduplication: repeated scheduler executions do not create duplicate push notifications")
    void testRepeatedExecutionDeduplication() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(new PushDispatchResult(1, 1, 0));

        // First execution: push should be dispatched
        scheduler.processDueReminders();
        verify(pushNotificationService, times(1)).sendToUser(eq(sampleUser.getId()), any());

        // Second execution: push should be skipped due to deduplication key
        scheduler.processDueReminders();
        verify(pushNotificationService, times(1)).sendToUser(eq(sampleUser.getId()), any());
    }

    @Test
    @DisplayName("processDueReminders handles push result with retry counts without breaking scheduler or email flow")
    void testProcessDueRemindersWithRetryResult() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(preferencesRepository.findByUserId(sampleUser.getId())).thenReturn(Optional.empty());
        when(emailNotificationService.sendReminderNotification(any(), any(), any())).thenReturn(true);
        // Push result with 1 attempt, 1 success, 0 failed, 1 retry, 0 deactivated
        when(pushNotificationService.sendToUser(any(), any())).thenReturn(new PushDispatchResult(1, 1, 0, 1, 0));

        scheduler.processDueReminders();

        verify(emailNotificationService).sendReminderNotification(eq(sampleReminder), eq(sampleUser), any());
        verify(pushNotificationService).sendToUser(eq(sampleUser.getId()), any());
    }

    @Test
    @DisplayName("processDueReminders does nothing when no due reminders are found")
    void testProcessDueRemindersEmpty() {
        when(reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processDueReminders();

        verify(emailNotificationService, never()).sendReminderNotification(any(), any(), any());
        verify(pushNotificationService, never()).sendToUser(any(), any());
    }
}
