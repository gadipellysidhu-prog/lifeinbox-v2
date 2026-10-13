package com.lifeinbox.service;

import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.ReminderResponse;
import com.lifeinbox.dto.UpdateReminderRequest;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.mapper.ReminderMapper;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final ReminderMapper reminderMapper;

    public ReminderService(
            ReminderRepository reminderRepository,
            UserRepository userRepository,
            ReminderMapper reminderMapper
    ) {
        this.reminderRepository = reminderRepository;
        this.userRepository = userRepository;
        this.reminderMapper = reminderMapper;
    }

    @Transactional
    public ReminderResponse createReminder(CreateReminderRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Reminder reminder = reminderMapper.toEntity(request, user);
        Reminder savedReminder = reminderRepository.save(reminder);
        return reminderMapper.toResponse(savedReminder);
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> getReminders(UUID userId, String filter, String search) {
        List<Reminder> reminders;
        String normalizedFilter = filter != null ? filter.trim().toLowerCase() : "all";

        switch (normalizedFilter) {
            case "today" -> {
                LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
                Instant startOfDay = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant endOfDay = todayUtc.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
                reminders = reminderRepository.findAllByUserIdAndDueAtBetweenOrderByDueAtAsc(userId, startOfDay, endOfDay);
            }
            case "upcoming" -> {
                Instant now = Instant.now();
                Instant in30Days = now.plus(30, ChronoUnit.DAYS);
                reminders = reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                        userId, ReminderStatus.PENDING, now, in30Days);
            }
            case "overdue" -> {
                Instant now = Instant.now();
                reminders = reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                        userId, ReminderStatus.PENDING, now);
            }
            case "completed" -> {
                reminders = reminderRepository.findAllByUserIdAndStatusOrderByDueAtAsc(
                        userId, ReminderStatus.COMPLETED);
            }
            default -> {
                reminders = reminderRepository.findAllByUserIdOrderByDueAtAsc(userId);
            }
        }

        if (search != null && !search.isBlank()) {
            String term = search.toLowerCase().trim();
            reminders = reminders.stream()
                    .filter(r -> (r.getTitle() != null && r.getTitle().toLowerCase().contains(term))
                            || (r.getNotes() != null && r.getNotes().toLowerCase().contains(term)))
                    .toList();
        }

        return reminders.stream()
                .map(reminderMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReminderResponse getReminderById(UUID id, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));
        return reminderMapper.toResponse(reminder);
    }

    @Transactional
    public ReminderResponse updateReminder(UUID id, UpdateReminderRequest request, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        reminder.setTitle(request.title().trim());
        reminder.setNotes(request.notes() != null ? request.notes().trim() : null);
        reminder.setDueAt(request.dueAt());
        reminder.setPriority(request.priority());
        reminder.setCategory(request.category() != null ? request.category().trim() : null);

        Reminder updated = reminderRepository.save(reminder);
        return reminderMapper.toResponse(updated);
    }

    @Transactional
    public ReminderResponse completeReminder(UUID id, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        reminder.setStatus(ReminderStatus.COMPLETED);
        reminder.setCompletedAt(Instant.now());

        Reminder updated = reminderRepository.save(reminder);
        return reminderMapper.toResponse(updated);
    }

    @Transactional
    public ReminderResponse incompleteReminder(UUID id, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        reminder.setStatus(ReminderStatus.PENDING);
        reminder.setCompletedAt(null);

        Reminder updated = reminderRepository.save(reminder);
        return reminderMapper.toResponse(updated);
    }

    @Transactional
    public void deleteReminder(UUID id, UUID userId) {
        Reminder reminder = reminderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder not found with id: " + id));

        reminderRepository.delete(reminder);
    }
}
