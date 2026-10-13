package com.lifeinbox.security;

import com.lifeinbox.exception.InvalidCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new InvalidCredentialsException("No authenticated user found in security context");
    }

    public static UUID getCurrentUserId() {
        return getCurrentPrincipal().getId();
    }
}
