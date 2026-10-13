package com.lifeinbox.service;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.PushSubscriptionRequest;
import com.lifeinbox.dto.PushSubscriptionResponse;
import com.lifeinbox.dto.VapidPublicKeyResponse;
import com.lifeinbox.entity.PushSubscription;
import com.lifeinbox.entity.User;
import com.lifeinbox.exception.DuplicateResourceException;
import com.lifeinbox.exception.ResourceNotFoundException;
import com.lifeinbox.repository.PushSubscriptionRepository;
import com.lifeinbox.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LifeInboxProperties properties;

    @InjectMocks
    private PushSubscriptionService pushSubscriptionService;

    private User sampleUser;
    private User anotherUser;
    private UUID userId;
    private UUID anotherUserId;
    private PushSubscriptionRequest sampleRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        anotherUserId = UUID.randomUUID();

        sampleUser = User.builder()
                .id(userId)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("pwd")
                .build();

        anotherUser = User.builder()
                .id(anotherUserId)
                .name("Bob")
                .email("bob@example.com")
                .passwordHash("pwd")
                .build();

        sampleRequest = new PushSubscriptionRequest(
                "https://fcm.googleapis.com/fcm/send/token-123",
                new PushSubscriptionRequest.Keys("p256dh-key-abc", "auth-secret-xyz")
        );
    }

    @Test
    @DisplayName("getPublicKey returns configured public key and push status without private key")
    void testGetPublicKey() {
        LifeInboxProperties.Push pushProps = new LifeInboxProperties.Push();
        pushProps.setEnabled(true);
        pushProps.setVapidPublicKey("BEl62iUYgUivxIkv69yViEuiBIa...");
        pushProps.setVapidPrivateKey("SUPER_SECRET_PRIVATE_KEY");

        when(properties.getPush()).thenReturn(pushProps);

        VapidPublicKeyResponse response = pushSubscriptionService.getPublicKey();

        assertThat(response.publicKey()).isEqualTo("BEl62iUYgUivxIkv69yViEuiBIa...");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    @DisplayName("registerSubscription Case A: creates and marks active a new subscription")
    void testRegisterNewSubscription() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint())).thenReturn(Optional.empty());

        PushSubscription savedEntity = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .endpoint(sampleRequest.endpoint())
                .p256dh(sampleRequest.keys().p256dh())
                .auth(sampleRequest.keys().auth())
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(pushSubscriptionRepository.saveAndFlush(any(PushSubscription.class))).thenReturn(savedEntity);

        PushSubscriptionResponse response = pushSubscriptionService.registerSubscription(userId, sampleRequest);

        assertThat(response.id()).isEqualTo(savedEntity.getId());
        assertThat(response.endpoint()).isEqualTo(sampleRequest.endpoint());
        assertThat(response.active()).isTrue();
        verify(pushSubscriptionRepository).saveAndFlush(any(PushSubscription.class));
    }

    @Test
    @DisplayName("registerSubscription Case B: updates keys and reactivates existing subscription for same user")
    void testReactivateExistingSubscriptionSameUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        UUID existingId = UUID.randomUUID();
        PushSubscription existing = PushSubscription.builder()
                .id(existingId)
                .user(sampleUser)
                .endpoint(sampleRequest.endpoint())
                .p256dh("old-p256dh")
                .auth("old-auth")
                .active(false) // previously inactive
                .createdAt(Instant.now().minusSeconds(100))
                .updatedAt(Instant.now().minusSeconds(100))
                .build();

        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint())).thenReturn(Optional.of(existing));
        when(pushSubscriptionRepository.saveAndFlush(existing)).thenReturn(existing);

        PushSubscriptionResponse response = pushSubscriptionService.registerSubscription(userId, sampleRequest);

        assertThat(response.id()).isEqualTo(existingId);
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getP256dh()).isEqualTo("p256dh-key-abc");
        assertThat(existing.getAuth()).isEqualTo("auth-secret-xyz");
    }

    @Test
    @DisplayName("registerSubscription Case C: rejects hijacking when endpoint is already registered by another user")
    void testRejectHijackingAnotherUserEndpoint() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        PushSubscription existingAnotherUser = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(anotherUser) // belongs to Bob
                .endpoint(sampleRequest.endpoint())
                .p256dh("bob-p256dh")
                .auth("bob-auth")
                .active(true)
                .build();

        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint()))
                .thenReturn(Optional.of(existingAnotherUser));

        assertThatThrownBy(() -> pushSubscriptionService.registerSubscription(userId, sampleRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered by another account");

        verify(pushSubscriptionRepository, never()).saveAndFlush(existingAnotherUser);
    }

    @Test
    @DisplayName("registerSubscription throws ResourceNotFoundException if user does not exist")
    void testRegisterSubscriptionUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pushSubscriptionService.registerSubscription(userId, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("unsubscribe deactivates active subscription belonging to authenticated user")
    void testUnsubscribeSuccess() {
        PushSubscription existing = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(sampleUser)
                .endpoint(sampleRequest.endpoint())
                .active(true)
                .build();

        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint())).thenReturn(Optional.of(existing));

        pushSubscriptionService.unsubscribe(userId, sampleRequest.endpoint());

        assertThat(existing.isActive()).isFalse();
        verify(pushSubscriptionRepository).saveAndFlush(existing);
    }

    @Test
    @DisplayName("unsubscribe does not modify subscription belonging to another user")
    void testUnsubscribeDoesNotModifyAnotherUserSubscription() {
        PushSubscription existing = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(anotherUser) // belongs to Bob
                .endpoint(sampleRequest.endpoint())
                .active(true)
                .build();

        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint())).thenReturn(Optional.of(existing));

        pushSubscriptionService.unsubscribe(userId, sampleRequest.endpoint());

        // Should remain active
        assertThat(existing.isActive()).isTrue();
        verify(pushSubscriptionRepository, never()).saveAndFlush(existing);
    }

    @Test
    @DisplayName("unsubscribe is safely idempotent when endpoint is unknown or already inactive")
    void testUnsubscribeIdempotent() {
        when(pushSubscriptionRepository.findByEndpoint("https://unknown.endpoint")).thenReturn(Optional.empty());

        // Repeated calls do not throw exceptions
        pushSubscriptionService.unsubscribe(userId, "https://unknown.endpoint");
        pushSubscriptionService.unsubscribe(userId, "https://unknown.endpoint");

        verify(pushSubscriptionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("registerSubscription rejects invalid endpoint schemes (javascript:, data:, etc.)")
    void testRegisterInvalidEndpointScheme() {
        PushSubscriptionRequest invalidRequest = new PushSubscriptionRequest(
                "javascript:alert(1)",
                sampleRequest.keys()
        );

        assertThatThrownBy(() -> pushSubscriptionService.registerSubscription(userId, invalidRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Endpoint must be a valid HTTP or HTTPS URL");
    }

    @Test
    @DisplayName("registerSubscription handles DataIntegrityViolationException during concurrent insert race condition and throws DuplicateResourceException")
    void testConcurrentRegistrationRaceCondition() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(pushSubscriptionRepository.findByEndpoint(sampleRequest.endpoint())).thenReturn(Optional.empty());
        when(pushSubscriptionRepository.saveAndFlush(any(PushSubscription.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThatThrownBy(() -> pushSubscriptionService.registerSubscription(userId, sampleRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered by another account");
    }

    @Test
    @DisplayName("getPublicKey exposes only public key and never leaks private VAPID key")
    void testGetPublicKeyNeverLeaksPrivateKey() {
        LifeInboxProperties.Push pushProps = new LifeInboxProperties.Push();
        pushProps.setEnabled(true);
        pushProps.setVapidPublicKey("PUBLIC_VAPID_KEY_123");
        pushProps.setVapidPrivateKey("s3cr3tPr1v4t3K3y");
        when(properties.getPush()).thenReturn(pushProps);

        VapidPublicKeyResponse response = pushSubscriptionService.getPublicKey();

        assertThat(response.publicKey()).isEqualTo("PUBLIC_VAPID_KEY_123");
        assertThat(response.enabled()).isTrue();
        // Verify response contains no reference to private key
        assertThat(response.toString()).doesNotContain("s3cr3tPr1v4t3K3y");
    }
}
