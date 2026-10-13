package com.lifeinbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionDeleteRequest(
        @NotBlank(message = "Endpoint is required")
        @Size(max = 2048, message = "Endpoint cannot exceed 2048 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Endpoint must be a valid HTTP or HTTPS URL")
        String endpoint
) {
}
