package com.lifeinbox.repository;

import com.lifeinbox.entity.PushSubscription;
import com.lifeinbox.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushSubscriptionRepositoryTest {

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        testUser1 = userRepository.save(User.builder()
                .name("Alice Browser")
                .email("alice." + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .build());

        testUser2 = userRepository.save(User.builder()
                .name("Bob Browser")
                .email("bob." + UUID.randomUUID() + "@example.com")
                .passwordHash("$2a$10$hashedpassword")
                .build());
    }

    @Test
    @DisplayName("Persists push subscription with generated UUID, timestamps, and active state")
    void testPersistPushSubscription() {
        PushSubscription subscription = PushSubscription.builder()
                .user(testUser1)
                .endpoint("https://fcm.googleapis.com/fcm/send/test-token-1")
                .p256dh("BNcR...sample-p256dh-key")
                .auth("auth-secret-sample")
                .active(true)
                .build();

        PushSubscription saved = pushSubscriptionRepository.saveAndFlush(subscription);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getUser().getId()).isEqualTo(testUser1.getId());
        assertThat(saved.getEndpoint()).isEqualTo("https://fcm.googleapis.com/fcm/send/test-token-1");
        assertThat(saved.getP256dh()).isEqualTo("BNcR...sample-p256dh-key");
        assertThat(saved.getAuth()).isEqualTo("auth-secret-sample");
    }

    @Test
    @DisplayName("Finds subscription by endpoint and active state")
    void testFindByEndpointAndActiveState() {
        String endpoint = "https://updates.push.services.mozilla.com/wpush/v2/test-token-2";
        PushSubscription sub = pushSubscriptionRepository.saveAndFlush(PushSubscription.builder()
                .user(testUser1)
                .endpoint(endpoint)
                .p256dh("p256dh-key")
                .auth("auth-key")
                .active(true)
                .build());

        Optional<PushSubscription> found = pushSubscriptionRepository.findByEndpoint(endpoint);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(sub.getId());

        Optional<PushSubscription> activeFound = pushSubscriptionRepository.findByEndpointAndActiveTrue(endpoint);
        assertThat(activeFound).isPresent();

        // Deactivate subscription
        sub.setActive(false);
        sub.setLastFailureAt(Instant.now());
        pushSubscriptionRepository.saveAndFlush(sub);

        Optional<PushSubscription> inactiveFound = pushSubscriptionRepository.findByEndpointAndActiveTrue(endpoint);
        assertThat(inactiveFound).isEmpty();

        Optional<PushSubscription> stillFound = pushSubscriptionRepository.findByEndpoint(endpoint);
        assertThat(stillFound).isPresent();
        assertThat(stillFound.get().isActive()).isFalse();
    }

    @Test
    @DisplayName("Queries active subscriptions for a user correctly filtering inactive ones")
    void testFindAllByUserIdAndActiveTrue() {
        // User 1 has 2 active and 1 inactive subscriptions (e.g. Chrome, Edge active; old Firefox inactive)
        pushSubscriptionRepository.save(PushSubscription.builder()
                .user(testUser1)
                .endpoint("https://push.example.com/sub-chrome")
                .p256dh("p256dh-1")
                .auth("auth-1")
                .active(true)
                .build());

        pushSubscriptionRepository.save(PushSubscription.builder()
                .user(testUser1)
                .endpoint("https://push.example.com/sub-edge")
                .p256dh("p256dh-2")
                .auth("auth-2")
                .active(true)
                .build());

        pushSubscriptionRepository.save(PushSubscription.builder()
                .user(testUser1)
                .endpoint("https://push.example.com/sub-firefox-expired")
                .p256dh("p256dh-3")
                .auth("auth-3")
                .active(false)
                .build());

        // User 2 has 1 active subscription
        pushSubscriptionRepository.save(PushSubscription.builder()
                .user(testUser2)
                .endpoint("https://push.example.com/sub-user2")
                .p256dh("p256dh-4")
                .auth("auth-4")
                .active(true)
                .build());

        pushSubscriptionRepository.flush();

        List<PushSubscription> user1Active = pushSubscriptionRepository.findAllByUserIdAndActiveTrue(testUser1.getId());
        assertThat(user1Active).hasSize(2);
        assertThat(user1Active).allMatch(PushSubscription::isActive);

        List<PushSubscription> user1All = pushSubscriptionRepository.findAllByUserId(testUser1.getId());
        assertThat(user1All).hasSize(3);

        List<PushSubscription> user2Active = pushSubscriptionRepository.findAllByUserIdAndActiveTrue(testUser2.getId());
        assertThat(user2Active).hasSize(1);
        assertThat(user2Active.get(0).getEndpoint()).isEqualTo("https://push.example.com/sub-user2");

        assertThat(pushSubscriptionRepository.countByUserIdAndActiveTrue(testUser1.getId())).isEqualTo(2);
        assertThat(pushSubscriptionRepository.countByUserIdAndActiveTrue(testUser2.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Enforces unique constraint on endpoint across all subscriptions")
    void testEndpointUniquenessEnforced() {
        String sharedEndpoint = "https://fcm.googleapis.com/fcm/send/unique-endpoint-token";

        PushSubscription firstSub = PushSubscription.builder()
                .user(testUser1)
                .endpoint(sharedEndpoint)
                .p256dh("p256dh-a")
                .auth("auth-a")
                .active(true)
                .build();
        pushSubscriptionRepository.saveAndFlush(firstSub);

        PushSubscription duplicateSub = PushSubscription.builder()
                .user(testUser2) // even if different user
                .endpoint(sharedEndpoint)
                .p256dh("p256dh-b")
                .auth("auth-b")
                .active(true)
                .build();

        assertThatThrownBy(() -> pushSubscriptionRepository.saveAndFlush(duplicateSub))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Finds subscription by userId and endpoint for user isolation checks")
    void testFindByUserIdAndEndpoint() {
        String endpoint = "https://push.example.com/alice-endpoint";
        PushSubscription sub = pushSubscriptionRepository.saveAndFlush(PushSubscription.builder()
                .user(testUser1)
                .endpoint(endpoint)
                .p256dh("p256dh")
                .auth("auth")
                .active(true)
                .build());

        Optional<PushSubscription> aliceSub = pushSubscriptionRepository.findByUserIdAndEndpoint(testUser1.getId(), endpoint);
        assertThat(aliceSub).isPresent();
        assertThat(aliceSub.get().getId()).isEqualTo(sub.getId());

        // Bob should not be able to find Alice's subscription
        Optional<PushSubscription> bobLookup = pushSubscriptionRepository.findByUserIdAndEndpoint(testUser2.getId(), endpoint);
        assertThat(bobLookup).isEmpty();
    }

    @Test
    @DisplayName("Enforces referential integrity: cannot persist subscription for non-existent user")
    void testUserReferentialIntegrity() {
        User unpersistedUser = User.builder()
                .id(UUID.randomUUID())
                .name("Ghost User")
                .email("ghost@example.com")
                .passwordHash("hash")
                .build();

        PushSubscription invalidSub = PushSubscription.builder()
                .user(unpersistedUser)
                .endpoint("https://push.example.com/ghost-sub")
                .p256dh("p256dh")
                .auth("auth")
                .active(true)
                .build();

        assertThatThrownBy(() -> pushSubscriptionRepository.saveAndFlush(invalidSub))
                .isInstanceOf(Exception.class);
    }
}
