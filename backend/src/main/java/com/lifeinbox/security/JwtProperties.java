package com.lifeinbox.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for HMAC signing (minimum 32 bytes / 256 bits).
     */
    private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /**
     * Expiration duration in milliseconds (default 24 hours).
     */
    private long expirationMs = 86400000L;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret key must be at least 32 bytes (256 bits) in length for HMAC-SHA256 signing.");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("JWT expiration duration must be greater than 0 milliseconds.");
        }
    }
}
