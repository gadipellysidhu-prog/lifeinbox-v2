package com.lifeinbox.dto;

public record AuthResponse(
        UserResponse user,
        String accessToken
) {
}
