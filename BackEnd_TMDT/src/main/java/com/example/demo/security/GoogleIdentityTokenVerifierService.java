package com.example.demo.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Set;

@Service
public class GoogleIdentityTokenVerifierService implements GoogleIdentityTokenVerifier {

    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "accounts.google.com",
            "https://accounts.google.com"
    );

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityTokenVerifierService(@Value("${app.auth.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        ).setAudience(List.of(clientId)).build();
    }

    @Override
    public VerifiedGoogleIdentity verify(String idToken) {
        final GoogleIdToken verifiedToken;
        try {
            verifiedToken = verifier.verify(idToken);
        } catch (IOException | GeneralSecurityException exception) {
            throw new GoogleIdentityTokenVerifierUnavailableException(exception);
        }

        if (verifiedToken == null) {
            return null;
        }

        GoogleIdToken.Payload payload = verifiedToken.getPayload();
        if (!GOOGLE_ISSUERS.contains(payload.getIssuer())) {
            return null;
        }

        return new VerifiedGoogleIdentity(
                payload.getSubject(),
                payload.getEmail(),
                Boolean.TRUE.equals(payload.getEmailVerified()),
                (String) payload.get("name"),
                (String) payload.get("picture")
        );
    }
}
