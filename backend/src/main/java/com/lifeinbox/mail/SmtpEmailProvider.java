package com.lifeinbox.mail;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.dto.EmailMessage;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "lifeinbox.mail.provider", havingValue = "smtp")
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender mailSender;
    private final LifeInboxProperties properties;

    public SmtpEmailProvider(JavaMailSender mailSender, LifeInboxProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendEmail(EmailMessage message) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
        );

        String fromEmail = properties.getMail().getFromEmail();
        String fromName = properties.getMail().getFromName();

        if (fromName != null && !fromName.isBlank()) {
            helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
        } else {
            helper.setFrom(fromEmail);
        }

        if (message.recipientName() != null && !message.recipientName().isBlank()) {
            helper.setTo(new InternetAddress(message.recipientEmail(), message.recipientName(), StandardCharsets.UTF_8.name()));
        } else {
            helper.setTo(message.recipientEmail());
        }

        helper.setSubject(message.subject());

        if (message.textBody() != null && !message.textBody().isBlank()) {
            helper.setText(message.textBody(), message.htmlBody());
        } else {
            helper.setText(message.htmlBody(), true);
        }

        log.debug("Sending email via SMTP to: {}, subject: {}", message.recipientEmail(), message.subject());
        mailSender.send(mimeMessage);
        log.info("Email successfully dispatched via SMTP to: {}", message.recipientEmail());
    }
}
