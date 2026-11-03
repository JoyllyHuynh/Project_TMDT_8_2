package com.example.demo.security;

public interface GoogleIdentityTokenVerifier {

    VerifiedGoogleIdentity verify(String idToken);
}
