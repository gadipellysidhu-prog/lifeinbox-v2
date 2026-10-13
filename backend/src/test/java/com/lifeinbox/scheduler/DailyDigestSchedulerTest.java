package com.lifeinbox.scheduler;

import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyDigestSchedulerTest {

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private DailyDigestScheduler scheduler;

    private User sampleUser;
    private UserNotificationPreferences samplePreferences;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Helen")
                .email("helen@example.com")
                .build();
    }

    @Test
    @DisplayName("processDailyDigests dispatches digest when user local time is past configured digest time")
    void testProcessDailyDigestsEligible() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowInZone = Instant.now().atZone(zone);
        LocalDate todayInZone = nowInZone.toLocalDate();
        // Configure digest time to 1 minute in the past so it's reached
        LocalTime pastTime = nowInZone.toLocalTime().minusMinutes(5);

        samplePreferences = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(pastTime)
                .timezone("Asia/Kolkata")
                .build();

        when(preferencesRepository.findAllEnabledForDailyDigest()).thenReturn(List.of(samplePreferences));

        String dedupKey = "DAILY_DIGEST:" + sampleUser.getId() + ":" + todayInZone;
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.empty());

        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(sampleUser.getId()), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(sampleUser.getId()), eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        when(emailNotificationService.sendDailyDigestNotification(
                eq(sampleUser), eq(zone), eq(todayInZone.toString()), any(), any(), any()))
                .thenReturn(true);

        scheduler.processDailyDigests();

        verify(emailNotificationService).sendDailyDigestNotification(
                eq(sampleUser), eq(zone), eq(todayInZone.toString()), any(), any(), any());
    }

    @Test
    @DisplayName("processDailyDigests skips when user local time has not reached configured digest time")
    void testProcessDailyDigestsFutureLocalTime() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowInZone = Instant.now().atZone(zone);
        // Set digest time to 2 hours in the future
        LocalTime futureTime = nowInZone.toLocalTime().plusHours(2);

        samplePreferences = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(futureTime)
                .timezone("Asia/Kolkata")
                .build();

        when(preferencesRepository.findAllEnabledForDailyDigest()).thenReturn(List.of(samplePreferences));

        scheduler.processDailyDigests();

        verify(emailNotificationService, never()).sendDailyDigestNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processDailyDigests skips when digest was already sent for user's local date")
    void testProcessDailyDigestsAlreadySentToday() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowInZone = Instant.now().atZone(zone);
        LocalDate todayInZone = nowInZone.toLocalDate();
        LocalTime pastTime = nowInZone.toLocalTime().minusMinutes(5);

        samplePreferences = UserNotificationPreferences.builder()
                .user(sampleUser)
                .emailNotificationsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(pastTime)
                .timezone("Asia/Kolkata")
                .build();

        when(preferencesRepository.findAllEnabledForDailyDigest()).thenReturn(List.of(samplePreferences));

        String dedupKey = "DAILY_DIGEST:" + sampleUser.getId() + ":" + todayInZone;
        EmailDelivery sentDelivery = EmailDelivery.builder()
                .deduplicationKey(dedupKey)
                .status(EmailDeliveryStatus.SENT)
                .build();
        when(emailDeliveryRepository.findByDeduplicationKey(dedupKey)).thenReturn(Optional.of(sentDelivery));

        scheduler.processDailyDigests();

        verify(emailNotificationService, never()).sendDailyDigestNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("processDailyDigests isolates User A and User B reminders so Bob Stone gets 0 tasks and sai gets 1 Today, 1 Upcoming, 1 Overdue")
    void testProcessDailyDigestsTwoUsersIsolation() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowInZone = Instant.now().atZone(zone);
        LocalDate todayInZone = nowInZone.toLocalDate();
        LocalTime pastTime = nowInZone.toLocalTime().minusMinutes(5);

        // User A: Bob Stone (0 reminders)
        User userA = User.builder()
                .id(UUID.randomUUID())
                .name("Bob Stone")
                .email("bob@example.com")
                .build();

        UserNotificationPreferences prefsA = UserNotificationPreferences.builder()
                .user(userA)
                .emailNotificationsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(pastTime)
                .timezone("Asia/Kolkata")
                .build();

        // User B: sai (3 reminders: 1 today, 1 upcoming, 1 overdue)
        User userB = User.builder()
                .id(UUID.randomUUID())
                .name("sai")
                .email("gadipellysidhu@gmail.com")
                .build();

        UserNotificationPreferences prefsB = UserNotificationPreferences.builder()
                .user(userB)
                .emailNotificationsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(pastTime)
                .timezone("Asia/Kolkata")
                .build();

        Reminder saiTodayReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(userB)
                .title("Complete Assignment")
                .status(ReminderStatus.PENDING)
                .dueAt(nowInZone.toInstant())
                .build();

        Reminder saiUpcomingReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(userB)
                .title("Doctor Appointment")
                .status(ReminderStatus.PENDING)
                .dueAt(nowInZone.plusDays(3).toInstant())
                .build();

        Reminder saiOverdueReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(userB)
                .title("Pay Electricity Bill")
                .status(ReminderStatus.PENDING)
                .dueAt(nowInZone.minusDays(2).toInstant())
                .build();

        when(preferencesRepository.findAllEnabledForDailyDigest()).thenReturn(List.of(prefsA, prefsB));

        // Neither user has already received a digest today
        when(emailDeliveryRepository.findByDeduplicationKey(eq("DAILY_DIGEST:" + userA.getId() + ":" + todayInZone)))
                .thenReturn(Optional.empty());
        when(emailDeliveryRepository.findByDeduplicationKey(eq("DAILY_DIGEST:" + userB.getId() + ":" + todayInZone)))
                .thenReturn(Optional.empty());

        // User A queries return empty lists
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userA.getId()), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userA.getId()), eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // User B queries return User B's reminders
        Instant startOfToday = todayInZone.atStartOfDay(zone).toInstant();
        Instant endOfToday = todayInZone.atTime(LocalTime.MAX).atZone(zone).toInstant();
        Instant upcomingHorizon = todayInZone.plusDays(30).atTime(LocalTime.MAX).atZone(zone).toInstant();

        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userB.getId()), eq(ReminderStatus.PENDING), eq(startOfToday), eq(endOfToday)))
                .thenReturn(List.of(saiTodayReminder));

        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userB.getId()), eq(ReminderStatus.PENDING), eq(endOfToday), eq(upcomingHorizon)))
                .thenReturn(List.of(saiUpcomingReminder));

        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userB.getId()), eq(ReminderStatus.PENDING), eq(startOfToday)))
                .thenReturn(List.of(saiOverdueReminder));

        scheduler.processDailyDigests();

        // Verify Bob Stone receives digest with 0 tasks
        org.mockito.ArgumentCaptor<List<Reminder>> bobTodayCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<Reminder>> bobUpcomingCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<Reminder>> bobOverdueCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        verify(emailNotificationService).sendDailyDigestNotification(
                eq(userA),
                eq(zone),
                eq(todayInZone.toString()),
                bobTodayCaptor.capture(),
                bobUpcomingCaptor.capture(),
                bobOverdueCaptor.capture()
        );

        org.assertj.core.api.Assertions.assertThat(bobTodayCaptor.getValue()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(bobUpcomingCaptor.getValue()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(bobOverdueCaptor.getValue()).isEmpty();

        // Verify sai receives digest with 1 Today, 1 Upcoming, 1 Overdue
        org.mockito.ArgumentCaptor<List<Reminder>> saiTodayCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<Reminder>> saiUpcomingCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<List<Reminder>> saiOverdueCaptor = org.mockito.ArgumentCaptor.forClass(List.class);

        verify(emailNotificationService).sendDailyDigestNotification(
                eq(userB),
                eq(zone),
                eq(todayInZone.toString()),
                saiTodayCaptor.capture(),
                saiUpcomingCaptor.capture(),
                saiOverdueCaptor.capture()
        );

        org.assertj.core.api.Assertions.assertThat(saiTodayCaptor.getValue()).containsExactly(saiTodayReminder);
        org.assertj.core.api.Assertions.assertThat(saiUpcomingCaptor.getValue()).containsExactly(saiUpcomingReminder);
        org.assertj.core.api.Assertions.assertThat(saiOverdueCaptor.getValue()).containsExactly(saiOverdueReminder);
    }

    @Test
    @DisplayName("processDailyDigests ensures User A reminders NEVER appear in User B digest")
    void testProcessDailyDigestsRemindersBelongingToUserANeverAppearInUserBDigest() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowInZone = Instant.now().atZone(zone);
        LocalDate todayInZone = nowInZone.toLocalDate();
        LocalTime pastTime = nowInZone.toLocalTime().minusMinutes(5);

        User userA = User.builder().id(UUID.randomUUID()).name("Bob Stone").email("bob@example.com").build();
        User userB = User.builder().id(UUID.randomUUID()).name("sai").email("gadipellysidhu@gmail.com").build();

        UserNotificationPreferences prefsA = UserNotificationPreferences.builder()
                .user(userA).emailNotificationsEnabled(true).dailyDigestEnabled(true)
                .dailyDigestTime(pastTime).timezone("Asia/Kolkata").build();
        UserNotificationPreferences prefsB = UserNotificationPreferences.builder()
                .user(userB).emailNotificationsEnabled(true).dailyDigestEnabled(true)
                .dailyDigestTime(pastTime).timezone("Asia/Kolkata").build();

        Reminder bobSecretReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(userA)
                .title("Bob's Confidential Task")
                .status(ReminderStatus.PENDING)
                .dueAt(nowInZone.toInstant())
                .build();

        when(preferencesRepository.findAllEnabledForDailyDigest()).thenReturn(List.of(prefsA, prefsB));
        when(emailDeliveryRepository.findByDeduplicationKey(anyString())).thenReturn(Optional.empty());

        // Bob has 1 reminder
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userA.getId()), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bobSecretReminder));
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userA.getId()), eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // sai has 0 reminders
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userB.getId()), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userB.getId()), eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processDailyDigests();

        org.mockito.ArgumentCaptor<List<Reminder>> saiTodayCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(emailNotificationService).sendDailyDigestNotification(
                eq(userB), any(), any(), saiTodayCaptor.capture(), any(), any());

        // Verify Bob's reminder NEVER appears in sai's digest
        org.assertj.core.api.Assertions.assertThat(saiTodayCaptor.getValue()).doesNotContain(bobSecretReminder);
        org.assertj.core.api.Assertions.assertThat(saiTodayCaptor.getValue()).isEmpty();
    }
}
