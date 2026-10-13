package com.lifeinbox.service;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.NotificationType;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.User;
import com.lifeinbox.mail.EmailProvider;
import com.lifeinbox.mail.EmailTemplateService;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.ReminderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailProvider emailProvider;
    private final EmailTemplateService emailTemplateService;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final LifeInboxProperties properties;
    private final ReminderRepository reminderRepository;

    public EmailNotificationService(
            EmailProvider emailProvider,
            EmailTemplateService emailTemplateService,
            EmailDeliveryRepository emailDeliveryRepository,
            LifeInboxProperties properties
    ) {
        this(emailProvider, emailTemplateService, emailDeliveryRepository, properties, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmailNotificationService(
            EmailProvider emailProvider,
            EmailTemplateService emailTemplateService,
            EmailDeliveryRepository emailDeliveryRepository,
            LifeInboxProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ReminderRepository reminderRepository
    ) {
        this.emailProvider = emailProvider;
        this.emailTemplateService = emailTemplateService;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.properties = properties;
        this.reminderRepository = reminderRepository;
    }

    /**
     * Attempts to send a reminder notification with persistent database idempotency.
     *
     * @param reminder the reminder
     * @param user the reminder owner
     * @param userZone the user's configured timezone
     * @return true if successfully dispatched, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendReminderNotification(Reminder reminder, User user, ZoneId userZone) {
        String deduplicationKey = "REMINDER:" + reminder.getId() + ":" + reminder.getDueAt().toEpochMilli();

        // Check if already claimed or sent
        Optional<EmailDelivery> existing = emailDeliveryRepository.findByDeduplicationKey(deduplicationKey);
        if (existing.isPresent()) {
            EmailDelivery delivery = existing.get();
            if (delivery.getStatus() == EmailDeliveryStatus.SENT || delivery.getStatus() == EmailDeliveryStatus.PROCESSING) {
                log.debug("Skipping already claimed/sent reminder notification for key: {}", deduplicationKey);
                return false;
            }
        }

        EmailMessage message = emailTemplateService.buildReminderEmail(reminder, user, userZone);

        EmailDelivery delivery;
        if (existing.isPresent()) {
            delivery = existing.get();
            delivery.setStatus(EmailDeliveryStatus.PROCESSING);
            delivery.setLastAttemptAt(Instant.now());
        } else {
            delivery = EmailDelivery.builder()
                    .user(user)
                    .reminder(reminder)
                    .notificationType(NotificationType.REMINDER)
                    .deduplicationKey(deduplicationKey)
                    .recipientEmail(user.getEmail())
                    .subject(message.subject())
                    .status(EmailDeliveryStatus.PROCESSING)
                    .attemptCount(0)
                    .lastAttemptAt(Instant.now())
                    .build();
            try {
                delivery = emailDeliveryRepository.saveAndFlush(delivery);
            } catch (DataIntegrityViolationException ex) {
                log.info("Concurrent worker claimed reminder notification key: {}. Skipping.", deduplicationKey);
                return false;
            }
        }

        return dispatchAndRecord(delivery, message);
    }

    /**
     * Attempts to send a daily digest notification with persistent date-based idempotency.
     *
     * @param user the user
     * @param userZone the user's timezone
     * @param localDateIso the local date string (e.g. "2026-10-10")
     * @param todayReminders list of today's reminders
     * @param upcomingReminders list of upcoming reminders
     * @param overdueReminders list of overdue reminders
     * @return true if successfully dispatched, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendDailyDigestNotification(
            User user,
            ZoneId userZone,
            String localDateIso,
            List<Reminder> todayReminders,
            List<Reminder> upcomingReminders,
            List<Reminder> overdueReminders
    ) {
        String deduplicationKey = "DAILY_DIGEST:" + user.getId() + ":" + localDateIso;

        Optional<EmailDelivery> existing = emailDeliveryRepository.findByDeduplicationKey(deduplicationKey);
        if (existing.isPresent()) {
            EmailDelivery delivery = existing.get();
            if (delivery.getStatus() == EmailDeliveryStatus.SENT || delivery.getStatus() == EmailDeliveryStatus.PROCESSING) {
                log.debug("Skipping already claimed/sent daily digest for key: {}", deduplicationKey);
                return false;
            }
        }

        EmailMessage message = emailTemplateService.buildDailyDigestEmail(
                user, userZone, todayReminders, upcomingReminders, overdueReminders);

        EmailDelivery delivery;
        if (existing.isPresent()) {
            delivery = existing.get();
            delivery.setStatus(EmailDeliveryStatus.PROCESSING);
            delivery.setLastAttemptAt(Instant.now());
        } else {
            delivery = EmailDelivery.builder()
                    .user(user)
                    .notificationType(NotificationType.DAILY_DIGEST)
                    .deduplicationKey(deduplicationKey)
                    .recipientEmail(user.getEmail())
                    .subject(message.subject())
                    .status(EmailDeliveryStatus.PROCESSING)
                    .attemptCount(0)
                    .lastAttemptAt(Instant.now())
                    .build();
            try {
                delivery = emailDeliveryRepository.saveAndFlush(delivery);
            } catch (DataIntegrityViolationException ex) {
                log.info("Concurrent worker claimed daily digest key: {}. Skipping.", deduplicationKey);
                return false;
            }
        }

        return dispatchAndRecord(delivery, message);
    }

    /**
     * Retries a previously failed delivery.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retryDelivery(EmailDelivery delivery, ZoneId userZone) {
        log.info("Retrying delivery {} for recipient {} (attempt {})",
                delivery.getId(), delivery.getRecipientEmail(), delivery.getAttemptCount() + 1);

        EmailMessage message;
        if (delivery.getNotificationType() == NotificationType.REMINDER) {
            Reminder reminder = delivery.getReminder();
            if (reminder != null && !org.hibernate.Hibernate.isInitialized(reminder)) {
                reminder = reminderRepository.findById(reminder.getId()).orElse(null);
                delivery.setReminder(reminder);
            }

            if (reminder == null || reminder.getStatus() == com.lifeinbox.entity.ReminderStatus.COMPLETED) {
                log.info("Reminder for delivery {} was completed or deleted. Cancelling retry.", delivery.getId());
                delivery.setStatus(EmailDeliveryStatus.FAILED);
                delivery.setFailureReason("Reminder was deleted or completed before retry");
                emailDeliveryRepository.save(delivery);
                return false;
            }
            message = emailTemplateService.buildReminderEmail(reminder, delivery.getUser(), userZone);
        } else {
            // For failed generic messages or digests without direct cache, recreate basic fallback message
            message = new EmailMessage(
                    delivery.getRecipientEmail(),
                    delivery.getUser().getName(),
                    delivery.getSubject(),
                    "<p>Hello, please visit LifeInbox to view your latest plan: <a href=\""
                            + properties.getAppBaseUrl() + "/dashboard\">Dashboard</a></p>",
                    "Visit LifeInbox: " + properties.getAppBaseUrl() + "/dashboard"
            );
        }

        delivery.setStatus(EmailDeliveryStatus.PROCESSING);
        delivery.setLastAttemptAt(Instant.now());
        emailDeliveryRepository.saveAndFlush(delivery);

        return dispatchAndRecord(delivery, message);
    }

    /**
     * Sends an immediate sample reminder email to test SMTP connectivity.
     */
    public boolean sendTestReminderEmail(User user, ZoneId userZone) {
        Reminder sampleReminder = Reminder.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title("Sample LifeInbox Test Reminder")
                .notes("This is an instant test notification verifying that your LifeInbox email alerts are configured and operating properly.")
                .dueAt(Instant.now())
                .priority(com.lifeinbox.entity.ReminderPriority.HIGH)
                .category("System Test")
                .status(com.lifeinbox.entity.ReminderStatus.PENDING)
                .build();

        EmailMessage message = emailTemplateService.buildReminderEmail(sampleReminder, user, userZone);
        try {
            emailProvider.sendEmail(message);
            log.info("Successfully dispatched test reminder email to {}", user.getEmail());
            return true;
        } catch (Exception ex) {
            log.error("Failed to send test reminder email to {}: {}", user.getEmail(), ex.getMessage());
            throw new RuntimeException("Failed to send email: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()), ex);
        }
    }

    /**
     * Sends an immediate test daily digest email summarizing the user's active tasks.
     */
    public boolean sendTestDailyDigest(User user, ZoneId userZone) {
        LocalDate localDate = Instant.now().atZone(userZone).toLocalDate();
        Instant startOfToday = localDate.atStartOfDay(userZone).toInstant();
        Instant endOfToday = localDate.atTime(LocalTime.MAX).atZone(userZone).toInstant();
        Instant upcomingHorizon = localDate.plusDays(30).atTime(LocalTime.MAX).atZone(userZone).toInstant();

        List<Reminder> todayReminders = reminderRepository != null
                ? reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                        user.getId(), com.lifeinbox.entity.ReminderStatus.PENDING, startOfToday, endOfToday)
                : List.of();

        List<Reminder> upcomingReminders = reminderRepository != null
                ? reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                        user.getId(), com.lifeinbox.entity.ReminderStatus.PENDING, endOfToday, upcomingHorizon)
                : List.of();

        List<Reminder> overdueReminders = reminderRepository != null
                ? reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                        user.getId(), com.lifeinbox.entity.ReminderStatus.PENDING, startOfToday)
                : List.of();

        EmailMessage message = emailTemplateService.buildDailyDigestEmail(
                user, userZone, todayReminders, upcomingReminders, overdueReminders);

        try {
            emailProvider.sendEmail(message);
            log.info("Successfully dispatched test daily digest email to {}", user.getEmail());
            return true;
        } catch (Exception ex) {
            log.error("Failed to send test daily digest to {}: {}", user.getEmail(), ex.getMessage());
            throw new RuntimeException("Failed to send daily digest: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()), ex);
        }
    }

    private boolean dispatchAndRecord(EmailDelivery delivery, EmailMessage message) {
        try {
            emailProvider.sendEmail(message);
            delivery.setStatus(EmailDeliveryStatus.SENT);
            delivery.setSentAt(Instant.now());
            delivery.setFailureReason(null);
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            emailDeliveryRepository.save(delivery);
            log.info("Successfully delivered email notification [type: {}, recipient: {}]",
                    delivery.getNotificationType(), delivery.getRecipientEmail());
            return true;
        } catch (Exception ex) {
            log.warn("Failed to deliver email notification [type: {}, recipient: {}]: {}",
                    delivery.getNotificationType(), delivery.getRecipientEmail(), ex.getMessage());
            delivery.setStatus(EmailDeliveryStatus.FAILED);
            delivery.setFailureReason(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setLastAttemptAt(Instant.now());
            emailDeliveryRepository.save(delivery);
            return false;
        }
    }
}
