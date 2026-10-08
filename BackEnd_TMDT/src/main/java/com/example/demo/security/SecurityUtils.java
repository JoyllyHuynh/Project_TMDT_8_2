package com.example.demo.security;

import com.example.demo.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw ApiException.unauthorized("Bạn cần đăng nhập.");
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
