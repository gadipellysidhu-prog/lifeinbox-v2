package com.lifeinbox.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lifeinbox")
@Getter
@Setter
public class LifeInboxProperties {

    private String appBaseUrl = "http://localhost:5173";
    private Mail mail = new Mail();
    private Scheduler scheduler = new Scheduler();
    private Push push = new Push();

    private Resend resend = new Resend();

    @Getter
    @Setter
    public static class Mail {
        private String provider = "resend";
        private String fromEmail = "no-reply@lifeinbox.local";
        private String fromName = "LifeInbox";
        private int maxRetries = 3;
        private int retryBackoffSeconds = 60;
    }

    @Getter
    @Setter
    public static class Resend {
        private String apiKey = "";
        private String fromEmail = "LifeInbox <onboarding@resend.dev>";
        private String apiUrl = "https://api.resend.com/emails";

        @Override
        public String toString() {
            return "Resend(apiKey=" + (apiKey != null && !apiKey.isBlank() ? "[PROTECTED]" : "[EMPTY]") +
                    ", fromEmail=" + fromEmail +
                    ", apiUrl=" + apiUrl + ")";
        }
    }

    @Getter
    @Setter
    public static class Scheduler {
        private long reminderIntervalMs = 30000;
        private long digestIntervalMs = 60000;
    }

    @Getter
    @Setter
    public static class Push {
        private boolean enabled = false;
        private String vapidPublicKey = "";
        private String vapidPrivateKey = "";
        private String vapidSubject = "mailto:admin@lifeinbox.local";
        private Retry retry = new Retry();

        @Getter
        @Setter
        public static class Retry {
            private boolean enabled = true;
            private int maxAttempts = 3;
            private long initialBackoffMs = 1000L;
            private long maxBackoffMs = 10000L;

            @Override
            public String toString() {
                return "Retry(enabled=" + enabled +
                        ", maxAttempts=" + maxAttempts +
                        ", initialBackoffMs=" + initialBackoffMs +
                        ", maxBackoffMs=" + maxBackoffMs + ")";
            }
        }

        /**
         * Checks whether VAPID keys and subject are configured.
         */
        public boolean hasValidKeys() {
            return vapidPublicKey != null && !vapidPublicKey.trim().isEmpty()
                    && vapidPrivateKey != null && !vapidPrivateKey.trim().isEmpty()
                    && vapidSubject != null && !vapidSubject.trim().isEmpty();
        }

        @Override
        public String toString() {
            return "Push(enabled=" + enabled +
                    ", vapidPublicKey=" + (vapidPublicKey != null && !vapidPublicKey.isBlank() ? "[CONFIGURED]" : "[EMPTY]") +
                    ", vapidPrivateKey=" + (vapidPrivateKey != null && !vapidPrivateKey.isBlank() ? "[PROTECTED]" : "[EMPTY]") +
                    ", vapidSubject=" + vapidSubject +
                    ", retry=" + retry + ")";
        }
    }
}
