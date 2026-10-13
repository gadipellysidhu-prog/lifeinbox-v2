package com.lifeinbox.config;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
public class WebPushConfig {

    private static final Logger log = LoggerFactory.getLogger(WebPushConfig.class);

    private final LifeInboxProperties properties;

    public WebPushConfig(LifeInboxProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            log.info("Registered Bouncy Castle cryptographic provider for Web Push");
        }

        if (properties.getPush().isEnabled()) {
            if (properties.getPush().hasValidKeys()) {
                log.info("Web Push is ENABLED with valid VAPID keys and subject: {}",
                        properties.getPush().getVapidSubject());
            } else {
                log.warn("Web Push is ENABLED, but VAPID configuration is incomplete. " +
                        "Notifications will not be dispatched until valid keys and subject are provided.");
            }
        } else {
            log.info("Web Push notifications are DISABLED by configuration (LIFEINBOX_PUSH_ENABLED=false)");
        }
    }
}
