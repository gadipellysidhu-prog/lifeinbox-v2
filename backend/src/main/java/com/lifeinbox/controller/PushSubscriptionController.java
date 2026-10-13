package com.lifeinbox.controller;

import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.dto.PushSubscriptionDeleteRequest;
import com.lifeinbox.dto.PushSubscriptionRequest;
import com.lifeinbox.dto.PushSubscriptionResponse;
import com.lifeinbox.dto.VapidPublicKeyResponse;
import com.lifeinbox.security.SecurityUtils;
import com.lifeinbox.service.PushNotificationService;
import com.lifeinbox.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/push")
@Tag(name = "Push Notifications", description = "Browser Web Push subscription and VAPID key management APIs")
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final PushNotificationService pushNotificationService;

    @Autowired
    public PushSubscriptionController(
            PushSubscriptionService pushSubscriptionService,
            PushNotificationService pushNotificationService
    ) {
        this.pushSubscriptionService = pushSubscriptionService;
        this.pushNotificationService = pushNotificationService;
    }

    public PushSubscriptionController(PushSubscriptionService pushSubscriptionService) {
        this(pushSubscriptionService, null);
    }

    @GetMapping("/public-key")
    @Operation(summary = "Get VAPID public key", description = "Retrieves the server's VAPID public key required by browser PushManager to create push subscriptions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Public key retrieved successfully")
    })
    public ResponseEntity<VapidPublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok(pushSubscriptionService.getPublicKey());
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Register browser push subscription", description = "Registers or reactivates a Web Push subscription for the authenticated user")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Push subscription registered or reactivated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload or malformed keys"),
            @ApiResponse(responseCode = "401", description = "Full authentication required"),
            @ApiResponse(responseCode = "409", description = "Endpoint already registered by another account")
    })
    public ResponseEntity<PushSubscriptionResponse> registerSubscription(
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        PushSubscriptionResponse response = pushSubscriptionService.registerSubscription(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/subscriptions")
    @Operation(summary = "Unsubscribe browser push subscription", description = "Deactivates a Web Push subscription belonging to the authenticated user")
    @SecurityRequirement(name = "BearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Push subscription successfully deactivated"),
            @ApiResponse(responseCode = "400", description = "Endpoint missing or invalid"),
            @ApiResponse(responseCode = "401", description = "Full authentication required")
    })
    public ResponseEntity<Void> unsubscribe(
            @RequestBody(required = false) @Valid PushSubscriptionDeleteRequest request,
            @RequestParam(name = "endpoint", required = false) String endpointParam
    ) {
        String endpoint = (request != null && request.endpoint() != null) ? request.endpoint() : endpointParam;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            throw new IllegalArgumentException("Endpoint is required for unsubscription");
        }
        endpoint = endpoint.trim();
        if (endpoint.length() > 2048 || !endpoint.matches("^https?://\\S+$")) {
            throw new IllegalArgumentException("Endpoint must be a valid HTTP or HTTPS URL");
        }

        UUID userId = SecurityUtils.getCurrentUserId();
        pushSubscriptionService.unsubscribe(userId, endpoint);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    @Operation(summary = "Send test push notification", description = "Sends an immediate test push notification to the user's active browser subscriptions")
    @SecurityRequirement(name = "BearerAuth")
    public ResponseEntity<PushDispatchResult> sendTestNotification() {
        UUID userId = SecurityUtils.getCurrentUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("type", "TEST");
        data.put("route", "/dashboard");
        data.put("tag", "test-" + System.currentTimeMillis());
        PushNotification notification = new PushNotification(
                "LifeInbox Desktop Alert",
                "Desktop notifications are active and working perfectly!",
                data
        );
        PushDispatchResult result = pushNotificationService != null
                ? pushNotificationService.sendToUser(userId, notification)
                : new PushDispatchResult(0, 0, 0, 0, 0);
        return ResponseEntity.ok(result);
    }
}
