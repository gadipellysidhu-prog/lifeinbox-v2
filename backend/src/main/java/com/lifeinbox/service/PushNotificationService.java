package com.lifeinbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.entity.PushSubscription;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.push.WebPushClient;
import com.lifeinbox.repository.PushSubscriptionRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final WebPushClient webPushClient;
    private final LifeInboxProperties properties;
    private final ObjectMapper objectMapper;
    private PushBackoffDelayer backoffDelayer = PushBackoffDelayer.DEFAULT;

    @org.springframework.beans.factory.annotation.Autowired
    public PushNotificationService(
            PushSubscriptionRepository pushSubscriptionRepository,
            UserNotificationPreferencesRepository preferencesRepository,
            WebPushClient webPushClient,
            LifeInboxProperties properties,
            ObjectMapper objectMapper
    ) {
        this(pushSubscriptionRepository, preferencesRepository, webPushClient, properties, objectMapper, PushBackoffDelayer.DEFAULT);
    }

    public PushNotificationService(
            PushSubscriptionRepository pushSubscriptionRepository,
            WebPushClient webPushClient,
            LifeInboxProperties properties,
            ObjectMapper objectMapper
    ) {
        this(pushSubscriptionRepository, null, webPushClient, properties, objectMapper, PushBackoffDelayer.DEFAULT);
    }

    public PushNotificationService(
            PushSubscriptionRepository pushSubscriptionRepository,
            UserNotificationPreferencesRepository preferencesRepository,
            WebPushClient webPushClient,
            LifeInboxProperties properties,
            ObjectMapper objectMapper,
            PushBackoffDelayer backoffDelayer
    ) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.preferencesRepository = preferencesRepository;
        this.webPushClient = webPushClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.backoffDelayer = backoffDelayer != null ? backoffDelayer : PushBackoffDelayer.DEFAULT;
    }

    public void setBackoffDelayer(PushBackoffDelayer backoffDelayer) {
        this.backoffDelayer = backoffDelayer != null ? backoffDelayer : PushBackoffDelayer.DEFAULT;
    }

    /**
     * Sends a Web Push notification to all active browser subscriptions of the specified user.
     * Incorporates retry handling for transient failures, deactivation for permanent failures,
     * multi-subscription isolation, and structured observability metrics.
     *
     * @param userId       the target user's UUID
     * @param notification the notification payload
     * @return a PushDispatchResult summarizing attempted, successful, failed, retried, and deactivated counts
     */
    public PushDispatchResult sendToUser(UUID userId, PushNotification notification) {
        if (!properties.getPush().isEnabled()) {
            log.debug("Web Push is disabled by configuration. Skipping push notification for user {}", userId);
            return new PushDispatchResult(0, 0, 0, 0, 0);
        }

        if (preferencesRepository != null) {
            Optional<UserNotificationPreferences> prefsOpt = preferencesRepository.findByUserId(userId);
            if (prefsOpt.isPresent() && !prefsOpt.get().isPushNotificationsEnabled()) {
                log.debug("Push notification skipped: user push notifications disabled for user {}", userId);
                return new PushDispatchResult(0, 0, 0, 0, 0);
            }
        }

        if (!properties.getPush().hasValidKeys()) {
            log.warn("Web Push is enabled, but VAPID keys are missing or incomplete. Skipping push notification for user {}", userId);
            return new PushDispatchResult(0, 0, 0, 0, 0);
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId);
        if (subscriptions.isEmpty()) {
            log.debug("No active push subscriptions found for user {}", userId);
            return new PushDispatchResult(0, 0, 0, 0, 0);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize push notification payload for user {}: {}", userId, e.getMessage());
            return new PushDispatchResult(0, 0, 0, 0, 0);
        }

        LifeInboxProperties.Push.Retry retryConfig = properties.getPush().getRetry();
        boolean retryEnabled = retryConfig != null && retryConfig.isEnabled();
        int maxAttempts = (retryEnabled && retryConfig != null) ? Math.max(1, retryConfig.getMaxAttempts()) : 1;
        long initialBackoffMs = (retryConfig != null) ? retryConfig.getInitialBackoffMs() : 1000L;
        long maxBackoffMs = (retryConfig != null) ? retryConfig.getMaxBackoffMs() : 10000L;

        int attempted = 0;
        int successful = 0;
        int failed = 0;
        int retried = 0;
        int deactivated = 0;

        for (PushSubscription subscription : subscriptions) {
            attempted++;
            boolean delivered = false;
            int attempt = 0;

            while (attempt < maxAttempts && !delivered) {
                attempt++;
                if (attempt > 1) {
                    retried++;
                    long delay = calculateBackoff(attempt, initialBackoffMs, maxBackoffMs);
                    if (delay > 0) {
                        try {
                            backoffDelayer.delay(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            log.warn("Push delivery retry interrupted for subscription {} for user {}", subscription.getId(), userId);
                            subscription.setLastFailureAt(Instant.now());
                            pushSubscriptionRepository.save(subscription);
                            failed++;
                            break;
                        }
                    }
                    log.debug("Retrying push delivery to subscription {} for user {} (attempt {}/{})",
                            subscription.getId(), userId, attempt, maxAttempts);
                }

                try {
                    int statusCode = webPushClient.send(subscription, payloadJson);

                    if (statusCode >= 200 && statusCode < 300) {
                        subscription.setLastSuccessAt(Instant.now());
                        pushSubscriptionRepository.save(subscription);
                        successful++;
                        delivered = true;
                        log.info("Successfully delivered push notification to subscription {} for user {} (HTTP {})",
                                subscription.getId(), userId, statusCode);
                    } else if (isPermanentInvalidStatusCode(statusCode)) {
                        // Permanent failure (404 / 410): subscription expired or unsubscribed on provider
                        subscription.setActive(false);
                        subscription.setLastFailureAt(Instant.now());
                        pushSubscriptionRepository.save(subscription);
                        deactivated++;
                        failed++;
                        log.info("Push subscription {} for user {} is no longer valid (HTTP {}). Deactivated subscription after permanent delivery failure.",
                                subscription.getId(), userId, statusCode);
                        break; // Do not retry permanent failure
                    } else if (isRetryableStatusCode(statusCode)) {
                        // Transient failure (429 or 5xx)
                        if (attempt < maxAttempts) {
                            log.debug("Transient push delivery failure for subscription {} (HTTP {}). Will retry (attempt {}/{})",
                                    subscription.getId(), statusCode, attempt, maxAttempts);
                        } else {
                            subscription.setLastFailureAt(Instant.now());
                            pushSubscriptionRepository.save(subscription);
                            failed++;
                            log.warn("Failed to deliver push notification to subscription {} for user {} after {} attempts (HTTP {})",
                                    subscription.getId(), userId, maxAttempts, statusCode);
                        }
                    } else {
                        // Non-retryable client error (e.g. 400, 401, 403)
                        subscription.setLastFailureAt(Instant.now());
                        pushSubscriptionRepository.save(subscription);
                        failed++;
                        log.warn("Non-retryable push delivery failure for subscription {} for user {} (HTTP {})",
                                subscription.getId(), userId, statusCode);
                        break; // Do not retry client error
                    }
                } catch (Exception ex) {
                    if (isExpiredException(ex)) {
                        subscription.setActive(false);
                        subscription.setLastFailureAt(Instant.now());
                        pushSubscriptionRepository.save(subscription);
                        deactivated++;
                        failed++;
                        log.info("Push subscription {} for user {} is no longer valid (exception indicates expired/gone). Deactivated subscription after permanent delivery failure.",
                                subscription.getId(), userId);
                        break; // Do not retry permanent failure
                    } else if (isRetryableException(ex)) {
                        String sanitizedError = sanitizeLogMessage(ex.getMessage(), subscription.getEndpoint());
                        if (attempt < maxAttempts) {
                            log.debug("Transient exception delivering push to subscription {}: {}. Will retry (attempt {}/{})",
                                    subscription.getId(), sanitizedError, attempt, maxAttempts);
                        } else {
                            subscription.setLastFailureAt(Instant.now());
                            pushSubscriptionRepository.save(subscription);
                            failed++;
                            log.warn("Failed to deliver push notification to subscription {} for user {} after {} attempts: {}",
                                    subscription.getId(), userId, maxAttempts, sanitizedError);
                        }
                    } else {
                        subscription.setLastFailureAt(Instant.now());
                        pushSubscriptionRepository.save(subscription);
                        failed++;
                        String sanitizedError = sanitizeLogMessage(ex.getMessage(), subscription.getEndpoint());
                        log.warn("Unexpected non-retryable error delivering push notification to subscription {} for user {}: {}",
                                subscription.getId(), userId, sanitizedError);
                        break; // Do not retry unexpected non-retryable exception
                    }
                }
            }
        }

        log.info("Push delivery completed: userId={}, attempted={}, successful={}, failed={}, retried={}, deactivated={}",
                userId, attempted, successful, failed, retried, deactivated);

        return new PushDispatchResult(attempted, successful, failed, retried, deactivated);
    }

    boolean isPermanentInvalidStatusCode(int statusCode) {
        return statusCode == 404 || statusCode == 410;
    }

    boolean isRetryableStatusCode(int statusCode) {
        return statusCode == 429 || (statusCode >= 500 && statusCode < 600);
    }

    boolean isExpiredException(Exception ex) {
        if (ex == null) {
            return false;
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("404") || lower.contains("410") || lower.contains("not found")
                    || lower.contains("gone") || lower.contains("unregistered")) {
                return true;
            }
        }
        Throwable cause = ex.getCause();
        if (cause instanceof Exception && cause != ex) {
            return isExpiredException((Exception) cause);
        }
        return false;
    }

    boolean isRetryableException(Exception ex) {
        if (ex == null || isExpiredException(ex)) {
            return false;
        }
        if (ex instanceof java.io.IOException || ex instanceof java.net.SocketException) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("connection reset")
                    || lower.contains("connection refused") || lower.contains("temporarily unavailable")
                    || lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")
                    || lower.contains("429") || lower.contains("network")) {
                return true;
            }
        }
        Throwable cause = ex.getCause();
        if (cause instanceof Exception && cause != ex) {
            return isRetryableException((Exception) cause);
        }
        return false;
    }

    long calculateBackoff(int attempt, long initialBackoffMs, long maxBackoffMs) {
        if (attempt <= 1 || initialBackoffMs <= 0) {
            return 0L;
        }
        int shift = Math.min(attempt - 2, 30);
        long multiplier = 1L << shift;
        long delay = initialBackoffMs * multiplier;
        if (delay < 0 || delay > maxBackoffMs) {
            delay = maxBackoffMs;
        }
        return Math.max(0L, delay);
    }

    private String sanitizeLogMessage(String message, String endpoint) {
        if (message == null) {
            return "unknown error";
        }
        String sanitized = message;
        if (endpoint != null && !endpoint.isBlank()) {
            sanitized = sanitized.replace(endpoint, "[REDACTED_ENDPOINT]");
        }
        return sanitized.replaceAll("https?://\\S+", "[REDACTED_URL]");
    }
}
