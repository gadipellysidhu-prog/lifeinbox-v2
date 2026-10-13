package com.lifeinbox.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        Instant timestamp,
        Map<String, String> errors
) {
    public static ApiError of(String title, int status, String detail, String instance) {
        return new ApiError(
                "about:blank",
                title,
                status,
                detail,
                instance,
                Instant.now(),
                null
        );
    }

    public static ApiError withErrors(String title, int status, String detail, String instance, Map<String, String> errors) {
        return new ApiError(
                "about:blank",
                title,
                status,
                detail,
                instance,
                Instant.now(),
                errors
        );
    }
}
