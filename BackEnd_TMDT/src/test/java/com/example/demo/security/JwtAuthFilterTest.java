package com.example.demo.security;

import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.repository.AuthSessionRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String SECRET = "a-test-secret-key-that-is-at-least-32-bytes";
    private static final String EMAIL = "user@example.com";

    private AuthSessionRepository authSessionRepository;
    private CustomUserDetailsService userDetailsService;
    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        authSessionRepository = mock(AuthSessionRepository.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        jwtService = new JwtService(SECRET, 60_000, 60_000);
        filter = new JwtAuthFilter(jwtService, userDetailsService, authSessionRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidJwtWithActiveSessionAndUser() throws Exception {
        String token = jwtService.generateToken(5L, EMAIL, "USER", false);
        allowSession(token);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(activeUser());

        runFilter(token);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertNotNull(authentication.getPrincipal());
        verify(authSessionRepository).findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                eq(jwtService.sha256(token)),
                any(LocalDateTime.class)
        );
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        String expiredToken = new JwtService(SECRET, -60_000, -60_000)
                .generateToken(5L, EMAIL, "USER", false);

        runFilter(expiredToken);

        assertUnauthenticated();
        verify(authSessionRepository, never())
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any());
    }

    @Test
    void rejectsJwtWithInvalidSignature() throws Exception {
        String invalidSignatureToken = new JwtService(
                "a-different-test-secret-that-is-at-least-32-bytes",
                60_000,
                60_000
        ).generateToken(5L, EMAIL, "USER", false);

        runFilter(invalidSignatureToken);

        assertUnauthenticated();
        verify(authSessionRepository, never())
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any());
    }

    @Test
    void rejectsTokenWithoutAnActiveAuthSession() throws Exception {
        String token = jwtService.generateToken(5L, EMAIL, "USER", false);
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                anyString(),
                any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        runFilter(token);

        assertUnauthenticated();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void rejectsRevokedAuthSession() throws Exception {
        String token = jwtService.generateToken(5L, EMAIL, "USER", false);
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                anyString(),
                any(LocalDateTime.class)
        )).thenReturn(Optional.empty());

        runFilter(token);

        assertUnauthenticated();
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void rejectsTokenForDeletedOrMissingUser() throws Exception {
        String token = jwtService.generateToken(5L, EMAIL, "USER", false);
        allowSession(token);
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new UsernameNotFoundException("missing"));

        runFilter(token);

        assertUnauthenticated();
    }

    @Test
    void rejectsTokenForLockedUser() throws Exception {
        String token = jwtService.generateToken(5L, EMAIL, "USER", false);
        allowSession(token);
        User user = new User();
        user.setId(5L);
        user.setEmail(EMAIL);
        user.setAccountStatus(AccountStatus.LOCKED);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(new UserPrincipal(user));

        runFilter(token);

        assertUnauthenticated();
    }

    @Test
    void doesNotAuthenticateWhenAuthorizationHeaderIsMissing() throws Exception {
        runFilter(null);

        assertUnauthenticated();
        verify(authSessionRepository, never())
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any());
    }

    private UserPrincipal activeUser() {
        User user = new User();
        user.setId(5L);
        user.setEmail(EMAIL);
        return new UserPrincipal(user);
    }

    private void allowSession(String token) {
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                eq(jwtService.sha256(token)),
                any(LocalDateTime.class)
        )).thenReturn(Optional.of(mock(com.example.demo.model.AuthSession.class)));
    }

    private void runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
    }

    private void assertUnauthenticated() {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
