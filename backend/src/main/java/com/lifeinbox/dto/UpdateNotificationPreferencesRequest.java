package com.lifeinbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record UpdateNotificationPreferencesRequest(
        @NotNull(message = "Email notifications toggle is required")
        Boolean emailNotificationsEnabled,

        @NotNull(message = "Reminder emails toggle is required")
        Boolean reminderEmailsEnabled,

        @NotNull(message = "Daily digest toggle is required")
        Boolean dailyDigestEnabled,

        @NotNull(message = "Daily digest time is required")
        LocalTime dailyDigestTime,

        @NotBlank(message = "Timezone is required")
        @Size(max = 100, message = "Timezone cannot exceed 100 characters")
        String timezone,

        Boolean pushNotificationsEnabled
) {
    public UpdateNotificationPreferencesRequest(
            Boolean emailNotificationsEnabled,
            Boolean reminderEmailsEnabled,
            Boolean dailyDigestEnabled,
            LocalTime dailyDigestTime,
            String timezone
    ) {
        this(emailNotificationsEnabled, reminderEmailsEnabled, dailyDigestEnabled, dailyDigestTime, timezone, true);
    }
}
