package com.lifeinbox.mail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.mail.resend.ResendApiException;
import com.lifeinbox.mail.resend.ResendClient;
import com.lifeinbox.mail.resend.ResendEmailRequest;
import com.lifeinbox.mail.resend.ResendEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResendEmailProviderTest {

    @Mock
    private ResendClient resendClient;

    private LifeInboxProperties properties;
    private ResendEmailProvider provider;

    private static final String SENSITIVE_API_KEY = "re_live_secret_key_abcdef123456789";

    @BeforeEach
    void setUp() {
        properties = new LifeInboxProperties();
        properties.getResend().setApiKey(SENSITIVE_API_KEY);
        properties.getResend().setFromEmail("LifeInbox <onboarding@resend.dev>");
        properties.getResend().setApiUrl("https://api.resend.com/emails");

        provider = new ResendEmailProvider(resendClient, properties);
    }

    @Test
    @DisplayName("1. Successful email delivery sends request to ResendClient and logs response ID")
    void testSuccessfulEmailDelivery() throws Exception {
        EmailMessage message = new EmailMessage(
                "john@example.com",
                "John Doe",
                "Important Reminder",
                "<p>Don't forget your task</p>",
                "Don't forget your task"
        );

        when(resendClient.sendEmail(anyString(), anyString(), any(ResendEmailRequest.class)))
                .thenReturn(new ResendEmailResponse("email_resend_9999"));

        provider.sendEmail(message);

        verify(resendClient).sendEmail(
                eq("https://api.resend.com/emails"),
                eq(SENSITIVE_API_KEY),
                any(ResendEmailRequest.class)
        );
    }

    @Test
    @DisplayName("2. Resend API failure re-throws exception for upstream error handling and retry recording")
    void testResendApiFailure() throws Exception {
        EmailMessage message = new EmailMessage(
                "bad-email@example.com",
                "Bad Email",
                "Failing Message",
                "<p>Test</p>",
                "Test"
        );

        when(resendClient.sendEmail(anyString(), anyString(), any(ResendEmailRequest.class)))
                .thenThrow(new ResendApiException("Resend API error (HTTP 422): Domain not verified", 422));

        assertThatThrownBy(() -> provider.sendEmail(message))
                .isInstanceOf(ResendApiException.class)
                .hasMessageContaining("Domain not verified");
    }

    @Test
    @DisplayName("3. Missing API key or configuration throws IllegalStateException without calling ResendClient")
    void testMissingApiKeyConfiguration() throws Exception {
        properties.getResend().setApiKey(""); // Empty API key

        EmailMessage message = new EmailMessage(
                "john@example.com",
                "John Doe",
                "Test Subject",
                "<p>Body</p>",
                "Body"
        );

        assertThatThrownBy(() -> provider.sendEmail(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Resend API key is not configured");

        verify(resendClient, never()).sendEmail(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("4. Sender configuration handles custom domain, formatted name, and onboarding fallback")
    void testSenderConfiguration() {
        // A. Custom domain with explicit display name
        properties.getResend().setFromEmail("LifeInbox Alerts <notifications@customdomain.com>");
        assertThat(provider.resolveSender()).isEqualTo("LifeInbox Alerts <notifications@customdomain.com>");

        // B. Custom domain email only - automatically merges with mail.fromName
        properties.getResend().setFromEmail("notifications@customdomain.com");
        properties.getMail().setFromName("LifeInbox Production");
        assertThat(provider.resolveSender()).isEqualTo("LifeInbox Production <notifications@customdomain.com>");

        // C. Resend fromEmail empty - falls back to Mail configuration
        properties.getResend().setFromEmail("");
        properties.getMail().setFromEmail("admin@mycompany.org");
        properties.getMail().setFromName("Company Admin");
        assertThat(provider.resolveSender()).isEqualTo("Company Admin <admin@mycompany.org>");

        // D. Fallback default onboarding sender
        properties.getResend().setFromEmail("");
        properties.getMail().setFromEmail("");
        assertThat(provider.resolveSender()).isEqualTo("LifeInbox <onboarding@resend.dev>");
    }

    @Test
    @DisplayName("5. Recipient, subject, HTML body, and plain-text body are mapped accurately to ResendEmailRequest")
    void testRecipientSubjectBodyMapping() throws Exception {
        EmailMessage message = new EmailMessage(
                "recipient@test.org",
                "Recipient Name",
                "Monthly Digest Report",
                "<h1>Summary</h1><p>All items completed.</p>",
                "Summary: All items completed."
        );

        when(resendClient.sendEmail(anyString(), anyString(), any(ResendEmailRequest.class)))
                .thenReturn(new ResendEmailResponse("resend_id_456"));

        provider.sendEmail(message);

        ArgumentCaptor<ResendEmailRequest> captor = ArgumentCaptor.forClass(ResendEmailRequest.class);
        verify(resendClient).sendEmail(anyString(), anyString(), captor.capture());

        ResendEmailRequest captured = captor.getValue();
        assertThat(captured.from()).isEqualTo("LifeInbox <onboarding@resend.dev>");
        assertThat(captured.to()).isEqualTo(List.of("recipient@test.org"));
        assertThat(captured.subject()).isEqualTo("Monthly Digest Report");
        assertThat(captured.html()).isEqualTo("<h1>Summary</h1><p>All items completed.</p>");
        assertThat(captured.text()).isEqualTo("Summary: All items completed.");
    }

    @Test
    @DisplayName("6. Sensitive data (API key) is never logged during normal operations, errors, or properties output")
    void testSensitiveDataIsNotLogged() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ResendEmailProvider.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            // Test 1: Successful send does not log API key
            when(resendClient.sendEmail(anyString(), anyString(), any(ResendEmailRequest.class)))
                    .thenReturn(new ResendEmailResponse("resend_id_789"));

            provider.sendEmail(new EmailMessage("test@example.com", "User", "Subject", "HTML", "TXT"));

            // Test 2: Error does not log API key
            when(resendClient.sendEmail(anyString(), anyString(), any(ResendEmailRequest.class)))
                    .thenThrow(new RuntimeException("Simulated network timeout"));

            assertThatThrownBy(() -> provider.sendEmail(
                    new EmailMessage("test@example.com", "User", "Subject", "HTML", "TXT")))
                    .isInstanceOf(RuntimeException.class);

            // Assert logger never saw the secret API key
            for (ILoggingEvent event : listAppender.list) {
                assertThat(event.getFormattedMessage()).doesNotContain(SENSITIVE_API_KEY);
            }

            // Assert properties.getResend().toString() masks the secret API key
            assertThat(properties.getResend().toString()).doesNotContain(SENSITIVE_API_KEY);
            assertThat(properties.getResend().toString()).contains("[PROTECTED]");
        } finally {
            logger.detachAppender(listAppender);
        }
    }
}
