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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PushSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;
    private final LifeInboxProperties properties;

    public PushSubscriptionService(
            PushSubscriptionRepository pushSubscriptionRepository,
            UserRepository userRepository,
            LifeInboxProperties properties
    ) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    /**
     * Retrieves the configured VAPID public key and push status.
     * Never exposes private keys or server secrets.
     */
    public VapidPublicKeyResponse getPublicKey() {
        return new VapidPublicKeyResponse(
                properties.getPush().getVapidPublicKey(),
                properties.getPush().isEnabled()
        );
    }

    /**
     * Registers or updates/reactivates a browser push subscription for the authenticated user.
     * Enforces user isolation: one user cannot hijack an endpoint belonging to another user.
     */
    @Transactional
    public PushSubscriptionResponse registerSubscription(UUID userId, PushSubscriptionRequest request) {
        if (request == null || request.endpoint() == null || !request.endpoint().matches("^https?://\\S+$")) {
            throw new IllegalArgumentException("Endpoint must be a valid HTTP or HTTPS URL");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Optional<PushSubscription> existingOpt = pushSubscriptionRepository.findByEndpoint(request.endpoint());

        if (existingOpt.isPresent()) {
            PushSubscription existing = existingOpt.get();

            // Check endpoint ownership
            if (!existing.getUser().getId().equals(userId)) {
                log.warn("User {} attempted to register push subscription endpoint already owned by another user", userId);
                throw new DuplicateResourceException("Push subscription endpoint is already registered by another account");
            }

            // Case B: Existing endpoint belonging to the same user -> update keys and reactivate
            existing.setP256dh(request.keys().p256dh());
            existing.setAuth(request.keys().auth());
            existing.setActive(true);

            PushSubscription updated = pushSubscriptionRepository.saveAndFlush(existing);
            log.info("Updated and reactivated push subscription {} for user {}", updated.getId(), userId);
            return mapToResponse(updated);
        }

        // Case A: New endpoint
        PushSubscription newSubscription = PushSubscription.builder()
                .user(user)
                .endpoint(request.endpoint())
                .p256dh(request.keys().p256dh())
                .auth(request.keys().auth())
                .active(true)
                .build();

        try {
            PushSubscription saved = pushSubscriptionRepository.saveAndFlush(newSubscription);
            log.info("Registered new push subscription {} for user {}", saved.getId(), userId);
            return mapToResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert race condition: check if same user just inserted it
            Optional<PushSubscription> raceOpt = pushSubscriptionRepository.findByEndpoint(request.endpoint());
            if (raceOpt.isPresent() && raceOpt.get().getUser().getId().equals(userId)) {
                PushSubscription raced = raceOpt.get();
                raced.setP256dh(request.keys().p256dh());
                raced.setAuth(request.keys().auth());
                raced.setActive(true);
                return mapToResponse(pushSubscriptionRepository.saveAndFlush(raced));
            }
            throw new DuplicateResourceException("Push subscription endpoint is already registered by another account");
        }
    }

    /**
     * Deactivates a push subscription belonging to the authenticated user.
     * Idempotent: repeated calls succeed safely.
     * Safe: users cannot deactivate other users' subscriptions.
     */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return;
        }

        Optional<PushSubscription> subOpt = pushSubscriptionRepository.findByEndpoint(endpoint);
        if (subOpt.isEmpty()) {
            log.debug("Unsubscribe called for unknown endpoint. Safely ignoring.");
            return;
        }

        PushSubscription subscription = subOpt.get();
        if (!subscription.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to unsubscribe endpoint owned by user {}. Safely ignoring.",
                    userId, subscription.getUser().getId());
            return;
        }

        if (subscription.isActive()) {
            subscription.setActive(false);
            pushSubscriptionRepository.saveAndFlush(subscription);
            log.info("Deactivated push subscription {} for user {}", subscription.getId(), userId);
        }
    }

    private PushSubscriptionResponse mapToResponse(PushSubscription sub) {
        return new PushSubscriptionResponse(
                sub.getId(),
                sub.getEndpoint(),
                sub.isActive(),
                sub.getCreatedAt(),
                sub.getUpdatedAt()
        );
    }
}
