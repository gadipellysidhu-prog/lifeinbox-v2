package com.lifeinbox.scheduler;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.entity.User;
import com.lifeinbox.entity.UserNotificationPreferences;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailRetrySchedulerTest {

    @Mock
    private EmailDeliveryRepository emailDeliveryRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private LifeInboxProperties properties;
    private EmailRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        properties.getMail().setMaxRetries(3);
        properties.getMail().setRetryBackoffSeconds(60);

        scheduler = new EmailRetryScheduler(
                emailDeliveryRepository,
                preferencesRepository,
                emailNotificationService,
                properties
        );
    }

    @Test
    @DisplayName("processFailedRetries retries eligible failed deliveries")
    void testProcessFailedRetries() {
        User user = User.builder().id(UUID.randomUUID()).build();
        EmailDelivery failedDelivery = EmailDelivery.builder()
                .id(UUID.randomUUID())
                .user(user)
                .status(EmailDeliveryStatus.FAILED)
                .attemptCount(1)
                .lastAttemptAt(Instant.now().minusSeconds(120))
                .build();

        when(emailDeliveryRepository.findEligibleForRetry(eq(EmailDeliveryStatus.FAILED), eq(3), any(Instant.class)))
                .thenReturn(List.of(failedDelivery));

        UserNotificationPreferences prefs = UserNotificationPreferences.builder()
                .timezone("Europe/London")
                .build();
        when(preferencesRepository.findByUserId(user.getId())).thenReturn(Optional.of(prefs));

        scheduler.processFailedRetries();

        verify(emailNotificationService).retryDelivery(failedDelivery, ZoneId.of("Europe/London"));
    }

    @Test
    @DisplayName("processFailedRetries does nothing when no failed deliveries are eligible")
    void testProcessFailedRetriesEmpty() {
        when(emailDeliveryRepository.findEligibleForRetry(eq(EmailDeliveryStatus.FAILED), eq(3), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processFailedRetries();

        verify(emailNotificationService, never()).retryDelivery(any(), any());
    }
}
