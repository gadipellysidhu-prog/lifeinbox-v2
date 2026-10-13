package com.lifeinbox.controller;

import com.lifeinbox.dto.CreateReminderRequest;
import com.lifeinbox.dto.ReminderResponse;
import com.lifeinbox.dto.UpdateReminderRequest;
import com.lifeinbox.security.SecurityUtils;
import com.lifeinbox.service.ReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reminders")
@Tag(name = "Reminders", description = "User-scoped reminder management and filtering APIs")
@SecurityRequirement(name = "BearerAuth")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    @Operation(summary = "List user reminders", description = "Retrieves reminders for the authenticated user with optional filter (today, upcoming, overdue, completed, all) and search term")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ReminderResponse>> getReminders(
            @RequestParam(required = false, defaultValue = "all") String filter,
            @RequestParam(required = false) String search
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<ReminderResponse> reminders = reminderService.getReminders(userId, filter, search);
        return ResponseEntity.ok(reminders);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reminder by ID", description = "Retrieves a specific reminder belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Reminder not found")
    })
    public ResponseEntity<ReminderResponse> getReminderById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ReminderResponse response = reminderService.getReminderById(id, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create reminder", description = "Creates a new reminder for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reminder created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ReminderResponse> createReminder(@Valid @RequestBody CreateReminderRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ReminderResponse response = reminderService.createReminder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update reminder", description = "Updates an existing reminder belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Reminder not found")
    })
    public ResponseEntity<ReminderResponse> updateReminder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReminderRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ReminderResponse response = reminderService.updateReminder(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Mark reminder as completed", description = "Marks a reminder as COMPLETED with completed timestamp")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder marked as completed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Reminder not found")
    })
    public ResponseEntity<ReminderResponse> completeReminder(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ReminderResponse response = reminderService.completeReminder(id, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/incomplete")
    @Operation(summary = "Mark reminder as incomplete/pending", description = "Marks a reminder as PENDING and resets completed timestamp")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder marked as incomplete/pending"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Reminder not found")
    })
    public ResponseEntity<ReminderResponse> incompleteReminder(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ReminderResponse response = reminderService.incompleteReminder(id, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete reminder", description = "Deletes a reminder belonging to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reminder deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Reminder not found")
    })
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        reminderService.deleteReminder(id, userId);
        return ResponseEntity.noContent().build();
    }
}
