package com.example.demo.service;

import com.example.demo.dto.auth.AuthResponse;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.ResetPasswordRequest;
import com.example.demo.model.AuthSession;
import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthSessionRepository;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.GoogleIdentityTokenVerifier;
import com.example.demo.security.JwtService;
import com.example.demo.security.VerifiedGoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthSessionRepository authSessionRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private GoogleIdentityTokenVerifier googleIdentityTokenVerifier;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authSessionRepository = mock(AuthSessionRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("ComicWormUnitTestSecretAtLeastThirtyTwoBytes", 60_000, 600_000);
        googleIdentityTokenVerifier = mock(GoogleIdentityTokenVerifier.class);
        authService = new AuthService(
                userRepository,
                authSessionRepository,
                passwordResetTokenRepository,
                authenticationManager,
                passwordEncoder,
                jwtService,
                mock(PasswordResetLinkNotifier.class),
                googleIdentityTokenVerifier
        );
    }

    @Test
    void registerHashesPasswordAndUsesSafeDefaultRole() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Nguyen Van A");
        request.setEmail("a@example.com");
        request.setPassword("password123");
        when(userRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        org.mockito.ArgumentCaptor<User> savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertTrue(passwordEncoder.matches(request.getPassword(), savedUser.getValue().getPasswordHash()));
        assertNotEquals(request.getPassword(), savedUser.getValue().getPasswordHash());
        assertEquals(UserRole.USER, savedUser.getValue().getRole());
        assertEquals(AccountStatus.ACTIVE, savedUser.getValue().getAccountStatus());
    }

    @Test
    void loginReturnsJwtAndPersistsOnlyItsHash() {
        User user = activeUser();
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password123");
        request.setRemember(true);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(user.getEmail())).thenReturn(Optional.of(user));
        doReturn(null).when(authenticationManager).authenticate(any());

        var response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals(user.getEmail(), response.getUser().getEmail());
        org.mockito.ArgumentCaptor<AuthSession> savedSession = org.mockito.ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(savedSession.capture());
        assertEquals(jwtService.sha256(response.getToken()), savedSession.getValue().getTokenHash());
        assertNotEquals(response.getToken(), savedSession.getValue().getTokenHash());
    }

    @Test
    void loginRejectsIncorrectPassword() {
        User user = activeUser();
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("incorrect-password");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        verify(authenticationManager).authenticate(any());
        verifyNoInteractions(authSessionRepository);
    }

    @Test
    void loginRejectsUnknownEmailBeforeAuthenticating() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@example.com");
        request.setPassword("password123");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail()))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.login(request)
        );

        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, exception.getStatus());
        verifyNoInteractions(authenticationManager, authSessionRepository);
    }

    @Test
    void passwordLoginRejectsGoogleOnlyAccount() {
        User user = activeUser();
        user.setPasswordHash(null);
        LoginRequest request = new LoginRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password123");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(authenticationManager, authSessionRepository);
    }

    @Test
    void googleLoginRejectsInvalidToken() {
        when(googleIdentityTokenVerifier.verify("invalid-token")).thenReturn(null);

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("invalid-token", false)
        );

        verifyNoInteractions(userRepository, authSessionRepository);
    }

    @Test
    void googleLoginRejectsTokenWithWrongAudience() {
        when(googleIdentityTokenVerifier.verify("wrong-audience-token")).thenReturn(null);

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("wrong-audience-token", false)
        );

        verifyNoInteractions(userRepository, authSessionRepository);
    }

    @Test
    void googleLoginRejectsUnverifiedEmail() {
        when(googleIdentityTokenVerifier.verify("unverified-email-token"))
                .thenReturn(googleIdentity(false));

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("unverified-email-token", false)
        );

        verifyNoInteractions(userRepository, authSessionRepository);
    }

    @Test
    void googleLoginUsesExistingGoogleAccountAndReturnsComicWormJwt() {
        User user = activeUser();
        user.setGoogleSub("google-sub-12");
        when(googleIdentityTokenVerifier.verify("valid-token")).thenReturn(googleIdentity(true));
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.of(user));

        AuthResponse response = authService.loginWithGoogle("valid-token", true);

        assertNotNull(response.getToken());
        assertEquals(user.getId(), response.getUser().getId());
        assertEquals(user.getEmail(), response.getUser().getEmail());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(authSessionRepository).save(any(AuthSession.class));
    }

    @Test
    void googleLoginCreatesSafeUserWhenNoAccountExists() {
        when(googleIdentityTokenVerifier.verify("new-user-token")).thenReturn(googleIdentity(true));
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        AuthResponse response = authService.loginWithGoogle("new-user-token", false);

        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertEquals("google-sub-12", savedUser.getValue().getGoogleSub());
        assertEquals("new.user@gmail.com", savedUser.getValue().getEmail());
        assertEquals("New User", savedUser.getValue().getFullName());
        assertEquals("https://example.com/avatar.png", savedUser.getValue().getAvatarUrl());
        assertEquals(null, savedUser.getValue().getPasswordHash());
        assertEquals(UserRole.USER, savedUser.getValue().getRole());
        assertEquals(false, savedUser.getValue().getSeller());
        assertEquals(AccountStatus.ACTIVE, savedUser.getValue().getAccountStatus());
        assertEquals(42L, response.getUser().getId());
        assertNotNull(response.getToken());
        verify(authSessionRepository).save(any(AuthSession.class));
    }

    @Test
    void googleLoginDoesNotAutomaticallyLinkExistingPasswordAccount() {
        User passwordAccount = activeUser();
        when(googleIdentityTokenVerifier.verify("existing-email-token")).thenReturn(googleIdentity(true));
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.of(passwordAccount));

        var exception = assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("existing-email-token", false)
        );

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(null, passwordAccount.getGoogleSub());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(authSessionRepository);
    }

    @Test
    void googleLoginRejectsDeletedAndLockedAccounts() {
        User deletedUser = activeUser();
        deletedUser.setGoogleSub("google-sub-12");
        deletedUser.setDeletedAt(LocalDateTime.now());
        when(googleIdentityTokenVerifier.verify("deleted-user-token")).thenReturn(googleIdentity(true));
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.of(deletedUser));

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("deleted-user-token", false)
        );
        verifyNoInteractions(authSessionRepository);

        User lockedUser = activeUser();
        lockedUser.setGoogleSub("google-sub-12");
        lockedUser.setAccountStatus(AccountStatus.LOCKED);
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.of(lockedUser));

        assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("valid-token", false)
        );
        verifyNoInteractions(authSessionRepository);
    }

    @Test
    void googleLoginReturnsConflictWhenUniqueConstraintWinsConcurrentCreation() {
        when(googleIdentityTokenVerifier.verify("racing-token")).thenReturn(googleIdentity(true));
        when(userRepository.findByGoogleSub("google-sub-12")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("new.user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var exception = assertThrows(
                com.example.demo.exception.ApiException.class,
                () -> authService.loginWithGoogle("racing-token", false)
        );

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verifyNoInteractions(authSessionRepository);
    }

    @Test
    void resetPasswordHashesNewPasswordConsumesTokenAndRevokesSessions() {
        User user = activeUser();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(jwtService.sha256("one-time-token"));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(passwordResetTokenRepository.findByTokenHashForUpdate(resetToken.getTokenHash()))
                .thenReturn(Optional.of(resetToken));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("one-time-token");
        request.setPassword("newPassword123");

        authService.resetPassword(request);

        assertTrue(passwordEncoder.matches(request.getPassword(), user.getPasswordHash()));
        assertNotEquals(request.getPassword(), user.getPasswordHash());
        assertNotNull(resetToken.getUsedAt());
        verify(authSessionRepository).deleteByUser(user);
    }

    private User activeUser() {
        User user = new User();
        user.setId(12L);
        user.setEmail("a@example.com");
        user.setFullName("Nguyen Van A");
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(UserRole.USER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }

    private VerifiedGoogleIdentity googleIdentity(boolean emailVerified) {
        return new VerifiedGoogleIdentity(
                "google-sub-12",
                "new.user@gmail.com",
                emailVerified,
                "New User",
                "https://example.com/avatar.png"
        );
    }
}
