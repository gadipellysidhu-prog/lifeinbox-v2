package com.lifeinbox.security;

import com.lifeinbox.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        properties.setExpirationMs(3600000L); // 1 hour

        jwtService = new JwtService(properties);

        userId = UUID.randomUUID();
        sampleUser = User.builder()
                .id(userId)
                .name("Sidharth")
                .email("sid@example.com")
                .passwordHash("hashedPwd")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("generateToken and validateToken: valid token returns true and correct claims")
    void testGenerateAndValidateToken() {
        String token = jwtService.generateToken(sampleUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("sid@example.com");
    }

    @Test
    @DisplayName("validateToken: invalid or malformed token returns false")
    void testInvalidToken() {
        assertThat(jwtService.validateToken("invalid.jwt.token")).isFalse();
        assertThat(jwtService.validateToken("")).isFalse();
    }
}
