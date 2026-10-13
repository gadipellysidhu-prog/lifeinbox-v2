package com.lifeinbox.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank(message = "Endpoint is required")
        @Size(max = 2048, message = "Endpoint cannot exceed 2048 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Endpoint must be a valid HTTP or HTTPS URL")
        String endpoint,

        @NotNull(message = "Subscription keys are required")
        @Valid
        Keys keys
) {
    public record Keys(
            @NotBlank(message = "p256dh key is required")
            @Size(max = 255, message = "p256dh key cannot exceed 255 characters")
            String p256dh,

            @NotBlank(message = "auth secret is required")
            @Size(max = 255, message = "auth secret cannot exceed 255 characters")
            String auth
    ) {
    }
}
