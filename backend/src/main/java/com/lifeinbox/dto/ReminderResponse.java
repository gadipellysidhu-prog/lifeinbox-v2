package com.lifeinbox.dto;

import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        String title,
        String notes,
        Instant dueAt,
        ReminderPriority priority,
        String category,
        ReminderStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
