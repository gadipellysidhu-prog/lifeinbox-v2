package com.lifeinbox.mail.resend;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendEmailRequest(
        String from,
        List<String> to,
        String subject,
        String html,
        String text
) {
}
