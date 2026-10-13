package com.lifeinbox.dto;

public record EmailMessage(
        String recipientEmail,
        String recipientName,
        String subject,
        String htmlBody,
        String textBody
) {
}
