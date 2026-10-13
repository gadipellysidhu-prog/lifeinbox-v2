package com.lifeinbox.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

/**
 * Strongly typed Web Push notification payload DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PushNotification(
        String title,
        String body,
        Map<String, Object> data
) {
    public PushNotification(String title, String body) {
        this(title, body, Collections.emptyMap());
    }

    public PushNotification {
        if (title == null || title.isBlank()) {
            title = "LifeInbox";
        }
        if (body == null) {
            body = "";
        }
        if (data == null) {
            data = Collections.emptyMap();
        }
    }
}
