package com.lifeinbox.scheduler;

import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.service.EmailNotificationService;
import com.lifeinbox.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ReminderEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailScheduler.class);

    private final ReminderRepository reminderRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final EmailNotificationService emailNotificationService;
    private final PushNotificationService pushNotificationService;

    private final ConcurrentMap<String, Instant> processedPushKeys = new ConcurrentHashMap<>();

    public ReminderEmailScheduler(
            ReminderRepository reminderRepository,
            UserNotificationPreferencesRepository preferencesRepository,
            EmailNotificationService emailNotificationService,
            PushNotificationService pushNotificationService
    ) {
        this.reminderRepository = reminderRepository;
        this.preferencesRepository = preferencesRepository;
        this.emailNotificationService = emailNotificationService;
        this.pushNotificationService = pushNotificationService;
    }

    @Scheduled(fixedDelayString = "${lifeinbox.scheduler.reminder-interval-ms:30000}", initialDelay = 5000)
    public void processDueReminders() {
        Instant now = Instant.now();
        List<Reminder> dueReminders = reminderRepository.findAllByStatusAndDueAtLessThanEqualWithUser(
                ReminderStatus.PENDING, now);

        if (dueReminders.isEmpty()) {
            return;
        }

        log.info("Found {} due pending reminders for notification processing", dueReminders.size());
        int processedCount = 0;
        int sentCount = 0;
        int skippedCount = 0;

        for (Reminder reminder : dueReminders) {
            processedCount++;
            User user = reminder.getUser();

            Optional<UserNotificationPreferences> prefsOpt = preferencesRepository.findByUserId(user.getId());
            boolean emailEnabled = true;
            if (prefsOpt.isPresent()) {
                UserNotificationPreferences prefs = prefsOpt.get();
                if (!prefs.isEmailNotificationsEnabled() || !prefs.isReminderEmailsEnabled()) {
                    log.debug("User {} has reminder emails disabled. Skipping email for reminder {}.", user.getId(), reminder.getId());
                    emailEnabled = false;
                    skippedCount++;
                }
            }

            // 1. Dispatch Email notification (if enabled in user email preferences)
            if (emailEnabled) {
                ZoneId userZone = prefsOpt.map(p -> {
                    try {
                        return ZoneId.of(p.getTimezone());
                    } catch (Exception e) {
                        return ZoneId.of("UTC");
                    }
                }).orElse(ZoneId.of("UTC"));

                try {
                    boolean sent = emailNotificationService.sendReminderNotification(reminder, user, userZone);
                    if (sent) {
                        sentCount++;
                    }
                } catch (Exception ex) {
                    log.error("Failed to send email notification for reminder {}: {}", reminder.getId(), ex.getMessage());
                }
            }

            // 2. Dispatch Web Push notification (independent channel, best-effort)
            boolean pushEnabled = prefsOpt.map(UserNotificationPreferences::isPushNotificationsEnabled).orElse(true);
            if (!pushEnabled) {
                log.debug("Push notification skipped: user push notifications disabled for user {}", user.getId());
            } else {
                dispatchPushNotification(reminder, user);
            }
        }

        log.info("Reminder notification run completed: {} examined, {} sent, {} skipped",
                processedCount, sentCount, skippedCount);
    }

    private void dispatchPushNotification(Reminder reminder, User user) {
        if (reminder == null || user == null) {
            return;
        }

        String pushDedupKey = "REMINDER:" + reminder.getId() + ":" + reminder.getDueAt().toEpochMilli();
        Instant now = Instant.now();

        // Evict expired entries (older than 24 hours) if cache grows large
        if (processedPushKeys.size() > 1000) {
            Instant cutoff = now.minus(24, ChronoUnit.HOURS);
            processedPushKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        }

        if (processedPushKeys.putIfAbsent(pushDedupKey, now) != null) {
            log.debug("Skipping already dispatched push notification for key: {}", pushDedupKey);
            return;
        }

        try {
            log.info("Sending push notification for reminder {}", reminder.getId());

            String title = "Reminder Due";
            String body = reminder.getTitle();
            if (body == null || body.isBlank()) {
                body = "You have a reminder due";
            } else {
                body = body.trim();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", "REMINDER_DUE");
            data.put("reminderId", reminder.getId().toString());
            data.put("route", "/dashboard");
            data.put("tag", "reminder-" + reminder.getId());

            PushNotification pushNotification = new PushNotification(title, body, data);
            PushDispatchResult result = pushNotificationService.sendToUser(user.getId(), pushNotification);

            if (result != null) {
                log.info("Push notification completed for reminder {}: attempted={}, successful={}, failed={}, retried={}, deactivated={}",
                        reminder.getId(), result.attempted(), result.successful(), result.failed(), result.retried(), result.deactivated());
            }
        } catch (Exception ex) {
            log.warn("Unexpected failure dispatching push notification for reminder {}: {}",
                    reminder.getId(), ex.getClass().getSimpleName());
        }
    }

    void clearProcessedPushKeys() {
        processedPushKeys.clear();
    }
}
