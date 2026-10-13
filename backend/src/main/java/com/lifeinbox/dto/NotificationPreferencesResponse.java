package com.lifeinbox.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record NotificationPreferencesResponse(
        UUID id,
        boolean emailNotificationsEnabled,
        boolean reminderEmailsEnabled,
        boolean dailyDigestEnabled,
        LocalTime dailyDigestTime,
        String timezone,
        Instant updatedAt,
        boolean pushNotificationsEnabled
) {
    public NotificationPreferencesResponse(
            UUID id,
            boolean emailNotificationsEnabled,
            boolean reminderEmailsEnabled,
            boolean dailyDigestEnabled,
            LocalTime dailyDigestTime,
            String timezone,
            Instant updatedAt
    ) {
        this(id, emailNotificationsEnabled, reminderEmailsEnabled, dailyDigestEnabled, dailyDigestTime, timezone, updatedAt, true);
    }
}
