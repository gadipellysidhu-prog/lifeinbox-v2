package com.lifeinbox.mapper;

import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.ReminderResponse;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ReminderMapper {

    public Reminder toEntity(CreateReminderRequest request, User user) {
        if (request == null) {
            return null;
        }
        return Reminder.builder()
                .user(user)
                .title(request.title().trim())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .dueAt(request.dueAt())
                .priority(request.priority())
                .category(request.category() != null ? request.category().trim() : null)
                .status(ReminderStatus.PENDING)
                .build();
    }

    public ReminderResponse toResponse(Reminder reminder) {
        if (reminder == null) {
            return null;
        }
        return new ReminderResponse(
                reminder.getId(),
                reminder.getTitle(),
                reminder.getNotes(),
                reminder.getDueAt(),
                reminder.getPriority(),
                reminder.getCategory(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt(),
                reminder.getCompletedAt()
        );
    }
}
