package com.example.demo.service;

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
import com.example.demo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private AuthSessionRepository authSessionRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private AuthenticationManager authenticationManager;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authSessionRepository = mock(AuthSessionRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("ComicWormUnitTestSecretAtLeastThirtyTwoBytes", 60_000, 600_000);
        authService = new AuthService(
                userRepository,
                authSessionRepository,
                passwordResetTokenRepository,
                authenticationManager,
                passwordEncoder,
                jwtService,
                mock(PasswordResetLinkNotifier.class)
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
}
