package com.lifeinbox.scheduler;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.entity.EmailDelivery;
import com.lifeinbox.entity.EmailDeliveryStatus;
import com.lifeinbox.repository.EmailDeliveryRepository;
import com.lifeinbox.repository.UserNotificationPreferencesRepository;
import com.lifeinbox.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Component
public class EmailRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailRetryScheduler.class);

    private final EmailDeliveryRepository emailDeliveryRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final EmailNotificationService emailNotificationService;
    private final LifeInboxProperties properties;

    public EmailRetryScheduler(
            EmailDeliveryRepository emailDeliveryRepository,
            UserNotificationPreferencesRepository preferencesRepository,
            EmailNotificationService emailNotificationService,
            LifeInboxProperties properties
    ) {
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.preferencesRepository = preferencesRepository;
        this.emailNotificationService = emailNotificationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void processFailedRetries() {
        int maxRetries = properties.getMail().getMaxRetries();
        int backoffSeconds = properties.getMail().getRetryBackoffSeconds();
        Instant eligibleBefore = Instant.now().minusSeconds(backoffSeconds);

        List<EmailDelivery> eligibleDeliveries = emailDeliveryRepository.findEligibleForRetry(
                EmailDeliveryStatus.FAILED, maxRetries, eligibleBefore);

        if (eligibleDeliveries.isEmpty()) {
            return;
        }

        log.info("Found {} failed email deliveries eligible for retry", eligibleDeliveries.size());
        int successCount = 0;

        for (EmailDelivery delivery : eligibleDeliveries) {
            ZoneId userZone = preferencesRepository.findByUserId(delivery.getUser().getId())
                    .map(p -> {
                        try {
                            return ZoneId.of(p.getTimezone());
                        } catch (Exception e) {
                            return ZoneId.of("UTC");
                        }
                    })
                    .orElse(ZoneId.of("UTC"));

            boolean retried = emailNotificationService.retryDelivery(delivery, userZone);
            if (retried) {
                successCount++;
            }
        }

        log.info("Retry run finished: {} attempted, {} succeeded", eligibleDeliveries.size(), successCount);
    }
}
