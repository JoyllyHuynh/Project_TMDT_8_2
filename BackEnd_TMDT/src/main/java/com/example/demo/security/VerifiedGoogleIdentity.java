package com.example.demo.security;

public record VerifiedGoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String name,
        String picture
) {
}
