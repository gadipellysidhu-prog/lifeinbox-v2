package com.lifeinbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.PushDispatchResult;
import com.lifeinbox.dto.PushNotification;
import com.lifeinbox.entity.PushSubscription;
import com.lifeinbox.entity.User;
import com.lifeinbox.push.WebPushClient;
import com.lifeinbox.repository.PushSubscriptionRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private WebPushClient webPushClient;

    private LifeInboxProperties properties;
    private ObjectMapper objectMapper;
    private PushNotificationService pushNotificationService;

    private UUID userId;
    private User testUser;
    private PushSubscription activeSub1;
    private PushSubscription activeSub2;

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        properties.getPush().setEnabled(true);
        properties.getPush().setVapidPublicKey("BEl62iUYgUivxIkv69yViEuiBIa-M9-26GoSlTk2PRtv5bVn5GCuh6h3BQ9gkUidrgrgWv9T2VOyzU02079675g");
        properties.getPush().setVapidPrivateKey("s3cr3tPr1v4t3K3yTh4tMustN3v3rB3L0gg3d0rExpos3d");
        properties.getPush().setVapidSubject("mailto:admin@lifeinbox.local");
        // Safe fast test backoff defaults
        properties.getPush().getRetry().setEnabled(true);
        properties.getPush().getRetry().setMaxAttempts(3);
        properties.getPush().getRetry().setInitialBackoffMs(1000);
        properties.getPush().getRetry().setMaxBackoffMs(10000);

        objectMapper = new ObjectMapper();
        pushNotificationService = new PushNotificationService(
                pushSubscriptionRepository,
                preferencesRepository,
                webPushClient,
                properties,
                objectMapper,
                PushBackoffDelayer.NOOP
        );

        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).email("user@example.com").name("Test User").build();

        activeSub1 = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://fcm.googleapis.com/fcm/send/token-1")
                .p256dh("p256dh-key-1")
                .auth("auth-key-1")
                .active(true)
                .build();

        activeSub2 = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://updates.push.services.mozilla.com/wpush/v2/token-2")
                .p256dh("p256dh-key-2")
                .auth("auth-key-2")
                .active(true)
                .build();
    }

    @Test
    void sendToUser_whenPushDisabled_doesNotAttemptDelivery() throws Exception {
        properties.getPush().setEnabled(false);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isZero();
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isZero();
        verifyNoInteractions(pushSubscriptionRepository);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void sendToUser_whenPushPreferenceDisabled_doesNotAttemptDelivery() throws Exception {
        com.lifeinbox.entity.UserNotificationPreferences prefs = com.lifeinbox.entity.UserNotificationPreferences.builder()
                .user(testUser)
                .pushNotificationsEnabled(false)
                .build();
        when(preferencesRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isZero();
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isZero();
        verifyNoInteractions(pushSubscriptionRepository);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void sendToUser_whenVapidKeysInvalid_doesNotAttemptDelivery() throws Exception {
        properties.getPush().setVapidPrivateKey("");

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isZero();
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isZero();
        verifyNoInteractions(pushSubscriptionRepository);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void sendToUser_whenNoActiveSubscriptions_returnsZeroWithoutCallingClient() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(Collections.emptyList());

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isZero();
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isZero();
        verify(pushSubscriptionRepository).findAllByUserIdAndActiveTrue(userId);
        verifyNoInteractions(webPushClient);
    }

    @Test
    void sendToUser_firstAttemptSucceeds_noRetryOccurs() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(201);

        PushNotification notification = new PushNotification("Due Reminder", "Tax payment due today");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isZero();
        assertThat(result.isSuccess()).isTrue();

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.getLastSuccessAt()).isNotNull();
        assertThat(activeSub1.isActive()).isTrue();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_withMultipleActiveSubscriptions_attemptsAllAndUpdatesSuccessTimestamps() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1, activeSub2));
        when(webPushClient.send(any(PushSubscription.class), anyString())).thenReturn(201);

        PushNotification notification = new PushNotification("Due Reminder", "Tax payment due today");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.successful()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isZero();
        assertThat(result.isSuccess()).isTrue();

        verify(webPushClient).send(eq(activeSub1), anyString());
        verify(webPushClient).send(eq(activeSub2), anyString());

        assertThat(activeSub1.getLastSuccessAt()).isNotNull();
        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub2.getLastSuccessAt()).isNotNull();
        assertThat(activeSub2.isActive()).isTrue();

        verify(pushSubscriptionRepository, times(2)).save(any(PushSubscription.class));
    }

    @Test
    void sendToUser_transientFailureOnFirstAttempt_secondAttemptSucceeds() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenReturn(500)  // Attempt 1 fails transiently
                .thenReturn(201);  // Attempt 2 (retry 1) succeeds

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.deactivated()).isZero();
        assertThat(result.isSuccess()).isTrue();

        verify(webPushClient, times(2)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub1.getLastSuccessAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_transientExceptionOnFirstAttempt_secondAttemptSucceeds() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenThrow(new RuntimeException("Connection timed out")) // Attempt 1 throws timeout
                .thenReturn(200);                                        // Attempt 2 succeeds

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.deactivated()).isZero();

        verify(webPushClient, times(2)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub1.getLastSuccessAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_rateLimit429_retriesAndSucceeds() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenReturn(429)  // Too Many Requests
                .thenReturn(201);  // Retry succeeds

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.retried()).isEqualTo(1);

        verify(webPushClient, times(2)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub1.getLastSuccessAt()).isNotNull();
    }

    @Test
    void sendToUser_repeatedTransientFailure_allAttemptsFail_keepsSubscriptionActive() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenReturn(500)
                .thenReturn(502)
                .thenReturn(503);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isEqualTo(2); // 2 retries after initial attempt
        assertThat(result.deactivated()).isZero();

        verify(webPushClient, times(3)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isTrue(); // Not deactivated because failure is transient
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_whenStatus404_deactivatesSubscriptionImmediately_noRetry() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(404);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isEqualTo(1);

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isFalse();
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_whenStatus410_deactivatesSubscriptionImmediately_noRetry() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(410);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isEqualTo(1);

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isFalse();
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_whenExceptionIndicates410Gone_deactivatesSubscriptionImmediately_noRetry() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenThrow(new RuntimeException("HTTP 410 Gone: subscription unregistered"));

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isEqualTo(1);

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isFalse();
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_nonRetryableClientError400_noRetry_keepsActive() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(400);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(result.deactivated()).isZero();

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_multipleSubscriptions_handlesTransientPermanentAndSuccessInIsolation() throws Exception {
        PushSubscription sub3 = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://push.apple.com/endpoint-3")
                .p256dh("p256dh-key-3")
                .auth("auth-key-3")
                .active(true)
                .build();

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1, activeSub2, sub3));

        // Sub 1: transient failure, then succeeds on retry
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(500).thenReturn(201);
        // Sub 2: permanent 410, deactivated immediately
        when(webPushClient.send(eq(activeSub2), anyString())).thenReturn(410);
        // Sub 3: first attempt succeeds
        when(webPushClient.send(eq(sub3), anyString())).thenReturn(201);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(3);
        assertThat(result.successful()).isEqualTo(2); // Sub 1 and Sub 3
        assertThat(result.failed()).isEqualTo(1);     // Sub 2
        assertThat(result.retried()).isEqualTo(1);    // Sub 1 retry
        assertThat(result.deactivated()).isEqualTo(1);// Sub 2 deactivated
        assertThat(result.isSuccess()).isTrue();

        assertThat(activeSub1.isActive()).isTrue();
        assertThat(activeSub1.getLastSuccessAt()).isNotNull();

        assertThat(activeSub2.isActive()).isFalse();
        assertThat(activeSub2.getLastFailureAt()).isNotNull();

        assertThat(sub3.isActive()).isTrue();
        assertThat(sub3.getLastSuccessAt()).isNotNull();

        verify(pushSubscriptionRepository).save(activeSub1);
        verify(pushSubscriptionRepository).save(activeSub2);
        verify(pushSubscriptionRepository).save(sub3);
    }

    @Test
    void sendToUser_whenRetryDisabledByConfiguration_onlyAttemptsOnce() throws Exception {
        properties.getPush().getRetry().setEnabled(false);

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(500);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.successful()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.retried()).isZero();

        verify(webPushClient, times(1)).send(eq(activeSub1), anyString());
    }

    @Test
    void sendToUser_customMaxAttemptsRespected() throws Exception {
        properties.getPush().getRetry().setMaxAttempts(2);

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(500);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.retried()).isEqualTo(1); // 1 retry attempt before reaching max 2

        verify(webPushClient, times(2)).send(eq(activeSub1), anyString());
    }

    @Test
    void sendToUser_backoffConfigurationRespected() throws Exception {
        List<Long> capturedDelays = new ArrayList<>();
        pushNotificationService.setBackoffDelayer(capturedDelays::add);

        properties.getPush().getRetry().setMaxAttempts(4);
        properties.getPush().getRetry().setInitialBackoffMs(500);
        properties.getPush().getRetry().setMaxBackoffMs(1500);

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString()))
                .thenReturn(500)
                .thenReturn(500)
                .thenReturn(500)
                .thenReturn(201); // Succeeds on attempt 4

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.retried()).isEqualTo(3);
        assertThat(capturedDelays).containsExactly(
                500L,   // attempt 2: initial
                1000L,  // attempt 3: 500 * 2
                1500L   // attempt 4: 500 * 4 = 2000, capped at maxBackoffMs (1500)
        );
    }

    @Test
    void calculateBackoff_exponentialWithMaxBoundAndOverflowSafety() {
        // attempt 1: immediate
        assertThat(pushNotificationService.calculateBackoff(1, 1000, 10000)).isEqualTo(0L);
        // attempt 2: initial
        assertThat(pushNotificationService.calculateBackoff(2, 1000, 10000)).isEqualTo(1000L);
        // attempt 3: 2x
        assertThat(pushNotificationService.calculateBackoff(3, 1000, 10000)).isEqualTo(2000L);
        // attempt 4: 4x
        assertThat(pushNotificationService.calculateBackoff(4, 1000, 10000)).isEqualTo(4000L);
        // attempt 5: 8x
        assertThat(pushNotificationService.calculateBackoff(5, 1000, 10000)).isEqualTo(8000L);
        // attempt 6: 16x (capped at 10000)
        assertThat(pushNotificationService.calculateBackoff(6, 1000, 10000)).isEqualTo(10000L);
        // zero initial backoff returns 0
        assertThat(pushNotificationService.calculateBackoff(2, 0, 10000)).isEqualTo(0L);
    }

    @Test
    void sendToUser_whenRetryInterrupted_handlesGracefully() throws Exception {
        pushNotificationService.setBackoffDelayer(millis -> {
            throw new InterruptedException("Thread interrupted");
        });

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(eq(activeSub1), anyString())).thenReturn(500);

        PushNotification notification = new PushNotification("Test", "Body");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(activeSub1.getLastFailureAt()).isNotNull();
        verify(pushSubscriptionRepository).save(activeSub1);
    }

    @Test
    void sendToUser_verifiesSerializedPayloadContainsNotificationData() throws Exception {
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(activeSub1));
        when(webPushClient.send(any(), anyString())).thenReturn(201);

        PushNotification notification = new PushNotification(
                "Custom Title",
                "Custom Body Text",
                Map.of("customKey", "customValue")
        );

        pushNotificationService.sendToUser(userId, notification);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(webPushClient).send(eq(activeSub1), payloadCaptor.capture());

        String capturedPayload = payloadCaptor.getValue();
        assertThat(capturedPayload).contains("\"title\":\"Custom Title\"");
        assertThat(capturedPayload).contains("\"body\":\"Custom Body Text\"");
        assertThat(capturedPayload).contains("\"customKey\":\"customValue\"");
    }

    @Test
    void sendToUser_multiSubscriptionIsolation_processesAllIndependently() throws Exception {
        PushSubscription subA = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://push.example.com/subA")
                .p256dh("p256dh-A")
                .auth("auth-A")
                .active(true)
                .build();

        PushSubscription subB = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://push.example.com/subB")
                .p256dh("p256dh-B")
                .auth("auth-B")
                .active(true)
                .build();

        PushSubscription subC = PushSubscription.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .endpoint("https://push.example.com/subC")
                .p256dh("p256dh-C")
                .auth("auth-C")
                .active(true)
                .build();

        // subA fails with 500 across all retry attempts
        when(webPushClient.send(eq(subA), anyString())).thenReturn(500);
        // subB succeeds immediately with 200
        when(webPushClient.send(eq(subB), anyString())).thenReturn(200);
        // subC fails permanently with 410 Gone (no retries, deactivated)
        when(webPushClient.send(eq(subC), anyString())).thenReturn(410);

        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId))
                .thenReturn(List.of(subA, subB, subC));

        PushNotification notification = new PushNotification("Isolation Test", "Testing three subscriptions");
        PushDispatchResult result = pushNotificationService.sendToUser(userId, notification);

        assertThat(result.attempted()).isEqualTo(3);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.deactivated()).isEqualTo(1);

        // subA failed transiently: remains active, failure timestamp recorded
        assertThat(subA.isActive()).isTrue();
        assertThat(subA.getLastFailureAt()).isNotNull();

        // subB succeeded: remains active, success timestamp recorded
        assertThat(subB.isActive()).isTrue();
        assertThat(subB.getLastSuccessAt()).isNotNull();

        // subC permanently invalid: deactivated
        assertThat(subC.isActive()).isFalse();
        assertThat(subC.getLastFailureAt()).isNotNull();
    }
}
