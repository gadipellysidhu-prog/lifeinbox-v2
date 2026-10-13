package com.lifeinbox.service;

import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.ReminderResponse;
import com.lifeinbox.dto.UpdateReminderRequest;
import com.lifeinbox.entity.Reminder;
import com.lifeinbox.entity.ReminderPriority;
import com.lifeinbox.entity.ReminderStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.mapper.ReminderMapper;
import com.lifeinbox.repository.ReminderRepository;
import com.lifeinbox.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReminderMapper reminderMapper;

    @InjectMocks
    private ReminderService reminderService;

    private User sampleUser;
    private Reminder sampleReminder;
    private ReminderResponse sampleResponse;
    private UUID userId;
    private UUID reminderId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reminderId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("hashedPwd")
                .build();

        sampleReminder = Reminder.builder()
                .id(reminderId)
                .user(sampleUser)
                .title("Dentist Appointment")
                .notes("Bring dental insurance card")
                .dueAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .priority(ReminderPriority.HIGH)
                .category("Health")
                .status(ReminderStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sampleResponse = new ReminderResponse(
                reminderId,
                "Dentist Appointment",
                "Bring dental insurance card",
                sampleReminder.getDueAt(),
                ReminderPriority.HIGH,
                "Health",
                ReminderStatus.PENDING,
                sampleReminder.getCreatedAt(),
                sampleReminder.getUpdatedAt(),
                null
        );
    }

    @Test
    @DisplayName("createReminder: successfully creates and saves reminder")
    void testCreateReminderSuccess() {
        CreateReminderRequest request = new CreateReminderRequest(
                "Dentist Appointment",
                "Bring dental insurance card",
                sampleReminder.getDueAt(),
                ReminderPriority.HIGH,
                "Health"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(reminderMapper.toEntity(request, sampleUser)).thenReturn(sampleReminder);
        when(reminderRepository.save(sampleReminder)).thenReturn(sampleReminder);
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        ReminderResponse response = reminderService.createReminder(request, userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(reminderId);
        assertThat(response.title()).isEqualTo("Dentist Appointment");
        verify(reminderRepository).save(sampleReminder);
    }

    @Test
    @DisplayName("createReminder: user not found throws ResourceNotFoundException")
    void testCreateReminderUserNotFound() {
        CreateReminderRequest request = new CreateReminderRequest(
                "Dentist Appointment",
                null,
                Instant.now(),
                ReminderPriority.MEDIUM,
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.createReminder(request, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id");

        verify(reminderRepository, never()).save(any(Reminder.class));
    }

    @Test
    @DisplayName("getReminders: returns all user reminders with default filter")
    void testGetRemindersAll() {
        when(reminderRepository.findAllByUserIdOrderByDueAtAsc(userId)).thenReturn(List.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "all", null);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).title()).isEqualTo("Dentist Appointment");
    }

    @Test
    @DisplayName("getReminders: filters today reminders within UTC day bounds")
    void testGetRemindersToday() {
        when(reminderRepository.findAllByUserIdAndDueAtBetweenOrderByDueAtAsc(eq(userId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "today", null);

        assertThat(list).hasSize(1);
        verify(reminderRepository).findAllByUserIdAndDueAtBetweenOrderByDueAtAsc(eq(userId), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("getReminders: filters upcoming pending reminders")
    void testGetRemindersUpcoming() {
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userId), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "upcoming", null);

        assertThat(list).hasSize(1);
        verify(reminderRepository).findAllByUserIdAndStatusAndDueAtBetweenOrderByDueAtAsc(
                eq(userId), eq(ReminderStatus.PENDING), any(Instant.class), any(Instant.class));
    }

    @Test
    @DisplayName("getReminders: filters overdue pending reminders")
    void testGetRemindersOverdue() {
        when(reminderRepository.findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userId), eq(ReminderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "overdue", null);

        assertThat(list).hasSize(1);
        verify(reminderRepository).findAllByUserIdAndStatusAndDueAtBeforeOrderByDueAtAsc(
                eq(userId), eq(ReminderStatus.PENDING), any(Instant.class));
    }

    @Test
    @DisplayName("getReminders: filters completed reminders")
    void testGetRemindersCompleted() {
        sampleReminder.setStatus(ReminderStatus.COMPLETED);
        when(reminderRepository.findAllByUserIdAndStatusOrderByDueAtAsc(userId, ReminderStatus.COMPLETED))
                .thenReturn(List.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "completed", null);

        assertThat(list).hasSize(1);
        verify(reminderRepository).findAllByUserIdAndStatusOrderByDueAtAsc(userId, ReminderStatus.COMPLETED);
    }

    @Test
    @DisplayName("getReminders: search term filters in-memory on title and notes")
    void testGetRemindersSearch() {
        Reminder other = Reminder.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .title("Buy Groceries")
                .notes("Milk and bread")
                .dueAt(Instant.now())
                .priority(ReminderPriority.LOW)
                .status(ReminderStatus.PENDING)
                .build();

        when(reminderRepository.findAllByUserIdOrderByDueAtAsc(userId)).thenReturn(List.of(sampleReminder, other));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        List<ReminderResponse> list = reminderService.getReminders(userId, "all", "dentist");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).title()).isEqualTo("Dentist Appointment");
    }

    @Test
    @DisplayName("getReminderById: returns reminder when found and owned by user")
    void testGetReminderByIdSuccess() {
        when(reminderRepository.findByIdAndUserId(reminderId, userId)).thenReturn(Optional.of(sampleReminder));
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        ReminderResponse response = reminderService.getReminderById(reminderId, userId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(reminderId);
    }

    @Test
    @DisplayName("ownership protection: getReminderById throws 404 when reminder belongs to another user")
    void testGetReminderByIdOwnershipProtection() {
        UUID otherUserId = UUID.randomUUID();
        when(reminderRepository.findByIdAndUserId(reminderId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.getReminderById(reminderId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder not found with id");
    }

    @Test
    @DisplayName("updateReminder: successfully updates owned reminder")
    void testUpdateReminderSuccess() {
        UpdateReminderRequest request = new UpdateReminderRequest(
                "Updated Dentist Title",
                "Updated notes",
                Instant.now().plus(3, ChronoUnit.DAYS),
                ReminderPriority.HIGH,
                "Health & Medical"
        );

        when(reminderRepository.findByIdAndUserId(reminderId, userId)).thenReturn(Optional.of(sampleReminder));
        when(reminderRepository.save(sampleReminder)).thenReturn(sampleReminder);
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        ReminderResponse response = reminderService.updateReminder(reminderId, request, userId);

        assertThat(response).isNotNull();
        assertThat(sampleReminder.getTitle()).isEqualTo("Updated Dentist Title");
        verify(reminderRepository).save(sampleReminder);
    }

    @Test
    @DisplayName("ownership protection: updateReminder throws 404 when reminder belongs to another user")
    void testUpdateReminderOwnershipProtection() {
        UUID otherUserId = UUID.randomUUID();
        UpdateReminderRequest request = new UpdateReminderRequest("Title", "Notes", Instant.now(), ReminderPriority.LOW, "Cat");

        when(reminderRepository.findByIdAndUserId(reminderId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.updateReminder(reminderId, request, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder not found with id");

        verify(reminderRepository, never()).save(any(Reminder.class));
    }

    @Test
    @DisplayName("completeReminder: sets status to COMPLETED and records completedAt")
    void testCompleteReminderSuccess() {
        when(reminderRepository.findByIdAndUserId(reminderId, userId)).thenReturn(Optional.of(sampleReminder));
        when(reminderRepository.save(sampleReminder)).thenReturn(sampleReminder);
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        ReminderResponse response = reminderService.completeReminder(reminderId, userId);

        assertThat(response).isNotNull();
        assertThat(sampleReminder.getStatus()).isEqualTo(ReminderStatus.COMPLETED);
        assertThat(sampleReminder.getCompletedAt()).isNotNull();
        verify(reminderRepository).save(sampleReminder);
    }

    @Test
    @DisplayName("ownership protection: completeReminder throws 404 when reminder belongs to another user")
    void testCompleteReminderOwnershipProtection() {
        UUID otherUserId = UUID.randomUUID();
        when(reminderRepository.findByIdAndUserId(reminderId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.completeReminder(reminderId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder not found with id");

        verify(reminderRepository, never()).save(any(Reminder.class));
    }

    @Test
    @DisplayName("incompleteReminder: sets status to PENDING and clears completedAt")
    void testIncompleteReminderSuccess() {
        sampleReminder.setStatus(ReminderStatus.COMPLETED);
        sampleReminder.setCompletedAt(Instant.now());

        when(reminderRepository.findByIdAndUserId(reminderId, userId)).thenReturn(Optional.of(sampleReminder));
        when(reminderRepository.save(sampleReminder)).thenReturn(sampleReminder);
        when(reminderMapper.toResponse(sampleReminder)).thenReturn(sampleResponse);

        ReminderResponse response = reminderService.incompleteReminder(reminderId, userId);

        assertThat(response).isNotNull();
        assertThat(sampleReminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(sampleReminder.getCompletedAt()).isNull();
        verify(reminderRepository).save(sampleReminder);
    }

    @Test
    @DisplayName("ownership protection: incompleteReminder throws 404 when reminder belongs to another user")
    void testIncompleteReminderOwnershipProtection() {
        UUID otherUserId = UUID.randomUUID();
        when(reminderRepository.findByIdAndUserId(reminderId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.incompleteReminder(reminderId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder not found with id");

        verify(reminderRepository, never()).save(any(Reminder.class));
    }

    @Test
    @DisplayName("deleteReminder: successfully deletes owned reminder")
    void testDeleteReminderSuccess() {
        when(reminderRepository.findByIdAndUserId(reminderId, userId)).thenReturn(Optional.of(sampleReminder));

        reminderService.deleteReminder(reminderId, userId);

        verify(reminderRepository).delete(sampleReminder);
    }

    @Test
    @DisplayName("ownership protection: deleteReminder throws 404 when reminder belongs to another user")
    void testDeleteReminderOwnershipProtection() {
        UUID otherUserId = UUID.randomUUID();
        when(reminderRepository.findByIdAndUserId(reminderId, otherUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reminderService.deleteReminder(reminderId, otherUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reminder not found with id");

        verify(reminderRepository, never()).delete(any(Reminder.class));
    }
}
