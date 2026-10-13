package com.lifeinbox.mapper;

import com.lifeinbox.dto.NotificationPreferencesResponse;
import com.lifeinbox.entity.UserNotificationPreferences;
import org.springframework.stereotype.Component;

@Component
public class NotificationPreferencesMapper {

    public NotificationPreferencesResponse toResponse(UserNotificationPreferences preferences) {
        if (preferences == null) {
            return null;
        }
        return new NotificationPreferencesResponse(
                preferences.getId(),
                preferences.isEmailNotificationsEnabled(),
                preferences.isReminderEmailsEnabled(),
                preferences.isDailyDigestEnabled(),
                preferences.getDailyDigestTime(),
                preferences.getTimezone(),
                preferences.getUpdatedAt(),
                preferences.isPushNotificationsEnabled()
        );
    }
}
