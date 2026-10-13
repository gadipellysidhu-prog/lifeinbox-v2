package com.lifeinbox.dto;

public record VapidPublicKeyResponse(
        String publicKey,
        boolean enabled
) {
}
