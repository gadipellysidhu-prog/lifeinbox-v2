package com.lifeinbox.push;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.entity.PushSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultWebPushClientTest {

    private LifeInboxProperties properties;
    private DefaultWebPushClient client;

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        client = new DefaultWebPushClient(properties);
    }

    @Test
    void send_whenVapidKeysNotConfigured_throwsIllegalStateException() {
        properties.getPush().setVapidPublicKey("");
        properties.getPush().setVapidPrivateKey("");

        PushSubscription subscription = PushSubscription.builder()
                .endpoint("https://example.com/push")
                .p256dh("p256dh")
                .auth("auth")
                .build();

        assertThatThrownBy(() -> client.send(subscription, "{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAPID keys are not configured or incomplete");
    }
}
