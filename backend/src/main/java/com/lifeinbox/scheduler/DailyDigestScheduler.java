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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class DailyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestScheduler.class);

    private final UserNotificationPreferencesRepository preferencesRepository;
    private final ReminderRepository reminderRepository;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final EmailNotificationService emailNotificationService;

    public DailyDigestScheduler(
            UserNotificationPreferencesRepository preferencesRepository,
            ReminderRepository reminderRepository,
            EmailDeliveryRepository emailDeliveryRepository,
            EmailNotificationService emailNotificationService
    ) {
        this.preferencesRepository = preferencesRepository;
        this.reminderRepository = reminderRepository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.emailNotificationService = emailNotificationService;
    }

    @Scheduled(fixedDelayString = "${lifeinbox.scheduler.digest-interval-ms:60000}", initialDelay = 10000)
    public void processDailyDigests() {
        List<UserNotificationPreferences> eligiblePreferences =
                preferencesRepository.findAllEnabledForDailyDigest();

        if (eligiblePreferences.isEmpty()) {
            return;
        }

        Instant nowUtc = Instant.now();
        int sentCount = 0;
        int skippedCount = 0;

        for (UserNotificationPreferences prefs : eligiblePreferences) {
            User user = prefs.getUser();
            ZoneId userZone;
            try {
                userZone = ZoneId.of(prefs.getTimezone());
            } catch (Exception e) {
                userZone = ZoneId.of("UTC");
            }

            ZonedDateTime nowInUserZone = nowUtc.atZone(userZone);
            LocalDate localDate = nowInUserZone.toLocalDate();
            LocalTime localTime = nowInUserZone.toLocalTime();

            // Check if user's local time has arrived for the morning digest
            if (localTime.isBefore(prefs.getDailyDigestTime())) {
                skippedCount++;
                continue;
            }

            String deduplicationKey = "DAILY_DIGEST:" + user.getId() + ":" + localDate;

            // Check persistent database idempotency (one digest per user per local calendar date)
            Optional<EmailDelivery> existing = emailDeliveryRepository.findByDeduplicationKey(deduplicationKey);
            if (existing.isPresent()) {
                EmailDelivery delivery = existing.get();
                if (delivery.getStatus() == EmailDeliveryStatus.SENT) {
                    skippedCount++;
                    continue;
                }
                if (delivery.getStatus() == EmailDeliveryStatus.PROCESSING
                        && delivery.getLastAttemptAt() != null
                        && delivery.getLastAttemptAt().isAfter(nowUtc.minus(5, ChronoUnit.MINUTES))) {
                    skippedCount++;
                    continue;
                }
            }

            // Calculate timezone-aware horizons for user's local calendar day
            Instant startOfToday = localDate.atStartOfDay(userZone).toInstant();
            Instant endOfToday = localDate.atTime(LocalTime.MAX).atZone(userZone).toInstant();
            Instant upcomingHorizon = localDate.plusDays(30).atTime(LocalTime.MAX).atZone(userZone).toInstant();

            List<Reminder> todayReminders = reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                    user.getId(), ReminderStatus.PENDING, startOfToday, endOfToday);

            List<Reminder> upcomingReminders = reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                    user.getId(), ReminderStatus.PENDING, endOfToday, upcomingHorizon);

            List<Reminder> overdueReminders = reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                    user.getId(), ReminderStatus.PENDING, startOfToday);

            boolean sent = emailNotificationService.sendDailyDigestNotification(
                    user,
                    userZone,
                    localDate.toString(),
                    todayReminders,
                    upcomingReminders,
                    overdueReminders
            );

            if (sent) {
                sentCount++;
            }
        }

        if (sentCount > 0) {
            log.info("Daily digest run finished: {} sent, {} skipped", sentCount, skippedCount);
        }
    }
}
