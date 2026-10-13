package com.lifeinbox.mail.resend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendErrorResponse(
        String name,
        String message,
        Integer statusCode
) {
}
