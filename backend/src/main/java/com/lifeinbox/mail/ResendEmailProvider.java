package com.lifeinbox.mail;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import com.lifeinbox.mail.resend.ResendClient;
import com.lifeinbox.mail.resend.ResendEmailRequest;
import com.lifeinbox.mail.resend.ResendEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@ConditionalOnProperty(name = "lifeinbox.mail.provider", havingValue = "resend", matchIfMissing = true)
public class ResendEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailProvider.class);

    private final ResendClient resendClient;
    private final LifeInboxProperties properties;

    public ResendEmailProvider(ResendClient resendClient, LifeInboxProperties properties) {
        this.resendClient = resendClient;
        this.properties = properties;
    }

    @Override
    public void sendEmail(EmailMessage message) throws Exception {
        String apiKey = properties.getResend().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Failed to send email to {}: Resend API key is not configured.", message.recipientEmail());
            throw new IllegalStateException("Resend API key is not configured. Set RESEND_API_KEY environment variable.");
        }

        String from = resolveSender();
        String to = message.recipientEmail();
        String subject = message.subject();
        String html = message.htmlBody();
        String text = message.textBody();

        ResendEmailRequest request = new ResendEmailRequest(
                from,
                List.of(to),
                subject,
                html,
                text
        );

        String apiUrl = properties.getResend().getApiUrl();
        log.debug("Dispatching email via Resend to: {}, subject: {}", to, subject);

        try {
            ResendEmailResponse response = resendClient.sendEmail(apiUrl, apiKey.trim(), request);
            log.info("Email successfully dispatched via Resend to: {} [resendId: {}]", to, response.id());
        } catch (Exception ex) {
            log.warn("Resend delivery failed for recipient {}: {}", to, ex.getMessage());
            throw ex;
        }
    }

    public String resolveSender() {
        String resendFrom = properties.getResend().getFromEmail();
        if (resendFrom != null && !resendFrom.trim().isEmpty()) {
            String trimmed = resendFrom.trim();
            // If sender is just an email address (e.g. "onboarding@resend.dev") and a name is configured, combine them
            if (!trimmed.contains("<") && !trimmed.contains(">")) {
                String fromName = properties.getMail().getFromName();
                if (fromName != null && !fromName.trim().isEmpty()) {
                    return fromName.trim() + " <" + trimmed + ">";
                }
            }
            return trimmed;
        }

        String mailFrom = properties.getMail().getFromEmail();
        if (mailFrom != null && !mailFrom.trim().isEmpty()) {
            String trimmedMailFrom = mailFrom.trim();
            String fromName = properties.getMail().getFromName();
            if (!trimmedMailFrom.contains("<") && !trimmedMailFrom.contains(">")
                    && fromName != null && !fromName.trim().isEmpty()) {
                return fromName.trim() + " <" + trimmedMailFrom + ">";
            }
            return trimmedMailFrom;
        }

        return "LifeInbox <onboarding@resend.dev>";
    }
}
