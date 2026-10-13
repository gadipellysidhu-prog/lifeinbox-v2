package com.lifeinbox.dto;

import com.lifeinbox.entity.ReminderPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReminderRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,

        @Size(max = 5000, message = "Notes cannot exceed 5000 characters")
        String notes,

        @NotNull(message = "Due date is required")
        Instant dueAt,

        @NotNull(message = "Priority is required")
        ReminderPriority priority,

        @Size(max = 100, message = "Category cannot exceed 100 characters")
        String category
) {
}
