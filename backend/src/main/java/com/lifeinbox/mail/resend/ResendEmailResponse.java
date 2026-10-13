package com.lifeinbox.mail.resend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendEmailResponse(
        String id
) {
}
