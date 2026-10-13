package com.lifeinbox.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesTitleBodyAndDataCorrectly() throws Exception {
        PushNotification notification = new PushNotification(
                "LifeInbox Alert",
                "Your task is due now",
                Map.of("reminderId", "123e4567-e89b-12d3-a456-426614174000", "priority", "HIGH")
        );

        String json = objectMapper.writeValueAsString(notification);

        assertThat(json).contains("\"title\":\"LifeInbox Alert\"");
        assertThat(json).contains("\"body\":\"Your task is due now\"");
        assertThat(json).contains("\"reminderId\":\"123e4567-e89b-12d3-a456-426614174000\"");
        assertThat(json).contains("\"priority\":\"HIGH\"");
    }

    @Test
    void handlesEmptyOptionalDataCorrectly() throws Exception {
        PushNotification notification = new PushNotification("LifeInbox", "Simple reminder");

        String json = objectMapper.writeValueAsString(notification);

        assertThat(json).contains("\"title\":\"LifeInbox\"");
        assertThat(json).contains("\"body\":\"Simple reminder\"");
        assertThat(json).contains("\"data\":{}");
    }

    @Test
    void appliesDefaultTitleAndBodyWhenBlankOrNull() {
        PushNotification notification = new PushNotification(null, null, null);

        assertThat(notification.title()).isEqualTo("LifeInbox");
        assertThat(notification.body()).isEmpty();
        assertThat(notification.data()).isEmpty();
    }

    @Test
    void payloadExposesOnlyIntendedFieldsAndExcludesSensitiveData() throws Exception {
        PushNotification notification = new PushNotification(
                "Reminder Due",
                "Buy groceries",
                Map.of("reminderId", "11111111-2222-3333-4444-555555555555", "type", "REMINDER_DUE", "route", "/dashboard")
        );

        String json = objectMapper.writeValueAsString(notification);

        assertThat(json).contains("\"title\":");
        assertThat(json).contains("\"body\":");
        assertThat(json).contains("\"data\":");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("secret");
        assertThat(json).doesNotContain("token");
        assertThat(json).doesNotContain("email");
        assertThat(json).doesNotContain("privateKey");
    }
}
