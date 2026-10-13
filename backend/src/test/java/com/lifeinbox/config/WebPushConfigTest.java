package com.lifeinbox.config;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushConfigTest {

    @Test
    @DisplayName("Default Push configuration is disabled with empty keys")
    void testDefaultPushProperties() {
        LifeInboxProperties properties = new LifeInboxProperties();
        LifeInboxProperties.Push push = properties.getPush();

        assertThat(push.isEnabled()).isFalse();
        assertThat(push.getVapidPublicKey()).isEmpty();
        assertThat(push.getVapidPrivateKey()).isEmpty();
        assertThat(push.getVapidSubject()).isEqualTo("mailto:admin@lifeinbox.local");
        assertThat(push.hasValidKeys()).isFalse();
    }

    @Test
    @DisplayName("hasValidKeys correctly evaluates completeness of VAPID parameters")
    void testHasValidKeys() {
        LifeInboxProperties.Push push = new LifeInboxProperties.Push();

        push.setVapidPublicKey("test-public-key");
        assertThat(push.hasValidKeys()).isFalse();

        push.setVapidPrivateKey("test-private-key");
        assertThat(push.hasValidKeys()).isTrue();

        push.setVapidSubject("   ");
        assertThat(push.hasValidKeys()).isFalse();

        push.setVapidSubject("mailto:admin@example.com");
        assertThat(push.hasValidKeys()).isTrue();
    }

    @Test
    @DisplayName("Push.toString masks the private key to prevent secret leakage in logs")
    void testPrivateKeyNeverLogged() {
        LifeInboxProperties.Push push = new LifeInboxProperties.Push();
        String sensitiveSecret = "SUPER_SECRET_PRIVATE_KEY_NEVER_LOG";
        push.setVapidPublicKey("public-key-value");
        push.setVapidPrivateKey(sensitiveSecret);

        String representation = push.toString();

        assertThat(representation).doesNotContain(sensitiveSecret);
        assertThat(representation).contains("[PROTECTED]");
        assertThat(representation).contains("[CONFIGURED]");
    }

    @Test
    @DisplayName("WebPushConfig registers BouncyCastleProvider in Java Security")
    void testBouncyCastleRegistration() {
        LifeInboxProperties properties = new LifeInboxProperties();
        WebPushConfig webPushConfig = new WebPushConfig(properties);

        webPushConfig.init();

        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();
    }

    @Test
    @DisplayName("WebPushConfig initializes cleanly when push is enabled with keys")
    void testWebPushConfigInitWhenEnabled() {
        LifeInboxProperties properties = new LifeInboxProperties();
        properties.getPush().setEnabled(true);
        properties.getPush().setVapidPublicKey("test-pub");
        properties.getPush().setVapidPrivateKey("test-priv");
        properties.getPush().setVapidSubject("mailto:admin@lifeinbox.local");

        WebPushConfig webPushConfig = new WebPushConfig(properties);

        // Should not throw exception
        webPushConfig.init();
        assertThat(properties.getPush().isEnabled()).isTrue();
    }
}
