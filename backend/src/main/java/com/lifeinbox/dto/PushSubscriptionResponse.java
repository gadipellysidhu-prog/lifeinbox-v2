package com.lifeinbox.dto;

import java.time.Instant;
import java.util.UUID;

public record PushSubscriptionResponse(
        UUID id,
        String endpoint,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
