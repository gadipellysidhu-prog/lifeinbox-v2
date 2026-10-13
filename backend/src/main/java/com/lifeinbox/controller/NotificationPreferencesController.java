package com.lifeinbox.controller;

import com.lifeinbox.dto.NotificationPreferencesResponse;
import com.lifeinbox.dto.UpdateNotificationPreferencesRequest;
import com.lifeinbox.security.SecurityUtils;
import com.lifeinbox.service.NotificationPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notification-preferences")
@Tag(name = "Notification Preferences", description = "User notification preferences and digest scheduling APIs")
@SecurityRequirement(name = "BearerAuth")
public class NotificationPreferencesController {

    private final NotificationPreferencesService preferencesService;

    public NotificationPreferencesController(NotificationPreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @GetMapping
    @Operation(summary = "Get user notification preferences", description = "Retrieves email notification and daily digest preferences for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<NotificationPreferencesResponse> getPreferences() {
        UUID userId = SecurityUtils.getCurrentUserId();
        NotificationPreferencesResponse response = preferencesService.getPreferences(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    @Operation(summary = "Update user notification preferences", description = "Updates email notifications, reminder alerts, daily digest schedule, and timezone for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or timezone"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        NotificationPreferencesResponse response = preferencesService.updatePreferences(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-email")
    @Operation(summary = "Send test reminder email", description = "Dispatches an immediate sample reminder email to the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test reminder email sent successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Email delivery failure")
    })
    public ResponseEntity<Map<String, String>> sendTestReminderEmail() {
        UUID userId = SecurityUtils.getCurrentUserId();
        preferencesService.sendTestReminderEmail(userId);
        return ResponseEntity.ok(Map.of("message", "Test reminder email sent successfully"));
    }

    @PostMapping("/test-digest")
    @Operation(summary = "Send test daily digest email", description = "Dispatches an immediate daily digest email summarizing the user's tasks")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Test daily digest email sent successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "500", description = "Email delivery failure")
    })
    public ResponseEntity<Map<String, String>> sendTestDailyDigest() {
        UUID userId = SecurityUtils.getCurrentUserId();
        preferencesService.sendTestDailyDigest(userId);
        return ResponseEntity.ok(Map.of("message", "Test daily digest email sent successfully"));
    }
}
