package com.lifeinbox.service;

import com.lifeinbox.dto.NotificationPreferencesResponse;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.mapper.NotificationPreferencesMapper;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class NotificationPreferencesService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPreferencesService.class);

    private final UserNotificationPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final NotificationPreferencesMapper preferencesMapper;
    private final EmailDeliveryRepository emailDeliveryRepository;
    private final EmailNotificationService emailNotificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationPreferencesService(
            UserNotificationPreferencesRepository preferencesRepository,
            UserRepository userRepository,
            NotificationPreferencesMapper preferencesMapper,
            EmailDeliveryRepository emailDeliveryRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EmailNotificationService emailNotificationService
    ) {
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
        this.preferencesMapper = preferencesMapper;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.emailNotificationService = emailNotificationService;
    }

    public NotificationPreferencesService(
            UserNotificationPreferencesRepository preferencesRepository,
            UserRepository userRepository,
            NotificationPreferencesMapper preferencesMapper
    ) {
        this(preferencesRepository, userRepository, preferencesMapper, null, null);
    }

    @Transactional
    public NotificationPreferencesResponse getPreferences(UUID userId) {
        UserNotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreferences(userId, null));
        return preferencesMapper.toResponse(preferences);
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(UUID userId, UpdateNotificationPreferencesRequest request) {
        validateTimezone(request.timezone());

        UserNotificationPreferences preferences = preferencesRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
                    return UserNotificationPreferences.builder().user(user).build();
                });

        boolean digestTimeChanged = preferences.getDailyDigestTime() != null
                && !preferences.getDailyDigestTime().equals(request.dailyDigestTime());

        preferences.setEmailNotificationsEnabled(request.emailNotificationsEnabled());
        preferences.setReminderEmailsEnabled(request.reminderEmailsEnabled());
        preferences.setDailyDigestEnabled(request.dailyDigestEnabled());
        preferences.setDailyDigestTime(request.dailyDigestTime());
        preferences.setTimezone(request.timezone().trim());
        if (request.pushNotificationsEnabled() != null) {
            preferences.setPushNotificationsEnabled(request.pushNotificationsEnabled());
        }

        if (digestTimeChanged && emailDeliveryRepository != null) {
            log.info("Daily digest time updated for user {}. Clearing existing delivery record so new schedule can fire.", userId);
            emailDeliveryRepository.deleteByUserIdAndDeduplicationKeyStartingWith(userId, "DAILY_DIGEST:" + userId);
        }

        UserNotificationPreferences saved = preferencesRepository.save(preferences);
        log.info("Updated notification preferences for user: {}", userId);
        return preferencesMapper.toResponse(saved);
    }

    @Transactional
    public UserNotificationPreferences createDefaultPreferences(UUID userId, String timezone) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        String resolvedZone = "UTC";
        if (timezone != null && !timezone.isBlank()) {
            try {
                ZoneId.of(timezone.trim());
                resolvedZone = timezone.trim();
            } catch (Exception e) {
                log.warn("Invalid timezone '{}' provided for user registration default, falling back to UTC", timezone);
            }
        }

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .user(user)
                .emailNotificationsEnabled(true)
                .reminderEmailsEnabled(true)
                .dailyDigestEnabled(true)
                .dailyDigestTime(LocalTime.of(8, 0))
                .timezone(resolvedZone)
                .pushNotificationsEnabled(true)
                .build();

        return preferencesRepository.save(prefs);
    }

    @Transactional(readOnly = true)
    public ZoneId getZoneIdForUser(UUID userId) {
        return preferencesRepository.findByUserId(userId)
                .map(prefs -> {
                    try {
                        return ZoneId.of(prefs.getTimezone());
                    } catch (Exception ex) {
                        return ZoneId.of("UTC");
                    }
                })
                .orElse(ZoneId.of("UTC"));
    }

    @Transactional
    public void sendTestReminderEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        ZoneId zoneId = getZoneIdForUser(userId);
        if (emailNotificationService != null) {
            emailNotificationService.sendTestReminderEmail(user, zoneId);
        } else {
            throw new IllegalStateException("Email service is not available");
        }
    }

    @Transactional
    public void sendTestDailyDigest(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        ZoneId zoneId = getZoneIdForUser(userId);
        if (emailNotificationService != null) {
            emailNotificationService.sendTestDailyDigest(user, zoneId);
        } else {
            throw new IllegalStateException("Email service is not available");
        }
    }

    private void validateTimezone(String timezoneStr) {
        if (timezoneStr == null || timezoneStr.isBlank()) {
            throw new IllegalArgumentException("Timezone cannot be blank");
        }
        try {
            ZoneId.of(timezoneStr.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid IANA timezone: " + timezoneStr);
        }
    }
}
