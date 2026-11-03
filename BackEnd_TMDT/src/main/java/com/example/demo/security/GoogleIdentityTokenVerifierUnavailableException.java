package com.example.demo.security;

public class GoogleIdentityTokenVerifierUnavailableException extends RuntimeException {

    public GoogleIdentityTokenVerifierUnavailableException(Throwable cause) {
        super("Google token verification is temporarily unavailable.", cause);
    }
}
