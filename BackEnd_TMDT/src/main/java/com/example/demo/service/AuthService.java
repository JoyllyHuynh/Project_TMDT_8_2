package com.example.demo.service;

import com.example.demo.dto.auth.AuthResponse;
import com.example.demo.dto.auth.ForgotPasswordRequest;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.ResetPasswordRequest;
import com.example.demo.dto.auth.UpdateProfileRequest;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.AuthSession;
import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.repository.AuthSessionRepository;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.GoogleIdentityTokenVerifier;
import com.example.demo.security.JwtService;
import com.example.demo.security.VerifiedGoogleIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int RESET_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetLinkNotifier passwordResetLinkNotifier;
    private final GoogleIdentityTokenVerifier googleIdentityTokenVerifier;

    @Value("${app.auth.password-reset.expiration-minutes:15}")
    private long resetTokenExpirationMinutes;

    @Value("${app.auth.password-reset.frontend-url:http://localhost:5500/vu/auth/reset-password/reset-password.html}")
    private String passwordResetFrontendUrl;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw ApiException.conflict("Email này đã được đăng ký.");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail().trim())
                .orElseThrow(() -> ApiException.unauthorized("Thông tin đăng nhập chưa chính xác."));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản đã bị khóa.");
        }
        if (user.getPasswordHash() == null) {
            throw ApiException.unauthorized("Thông tin đăng nhập chưa chính xác.");
        }

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                request.getPassword()
        ));

        return createAuthResponse(user, request.isRemember());
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idToken, boolean remember) {
        VerifiedGoogleIdentity identity = googleIdentityTokenVerifier.verify(idToken);
        if (identity == null
                || identity.subject() == null || identity.subject().isBlank()
                || identity.email() == null || identity.email().isBlank()
                || !identity.email().contains("@")
                || !identity.emailVerified()) {
            throw ApiException.unauthorized("Không thể xác minh tài khoản Google.");
        }

        User user = userRepository.findByGoogleSub(identity.subject())
                .orElseGet(() -> findOrCreateGoogleUser(identity));
        if (user.getDeletedAt() != null) {
            throw ApiException.forbidden("Tài khoản đã bị xóa và không thể đăng nhập.");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("Tài khoản đã bị khóa.");
        }

        return createAuthResponse(user, remember);
    }

    private User findOrCreateGoogleUser(VerifiedGoogleIdentity identity) {
        String email = identity.email().trim();
        userRepository.findByEmailIgnoreCase(email).ifPresent(existingUser -> {
            if (existingUser.getDeletedAt() != null) {
                throw ApiException.conflict("Email này thuộc tài khoản đã bị xóa.");
            }
            throw ApiException.conflict(
                    "Email này đã có tài khoản. Hãy đăng nhập bằng phương thức hiện tại."
            );
        });

        User user = new User();
        user.setGoogleSub(identity.subject());
        user.setEmail(email);
        String fullName = identity.name() == null || identity.name().isBlank()
                ? email.substring(0, email.indexOf('@'))
                : identity.name().trim();
        user.setFullName(fullName.length() > 100 ? fullName.substring(0, 100) : fullName);
        user.setAvatarUrl(identity.picture());

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict(
                    "Tài khoản Google đã được tạo đồng thời. Vui lòng thử đăng nhập lại."
            );
        }
    }

    private AuthResponse createAuthResponse(User user, boolean remember) {
        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                remember
        );

        AuthSession authSession = new AuthSession();
        authSession.setUser(user);
        authSession.setTokenHash(jwtService.sha256(token));
        authSession.setExpiresAt(LocalDateTime.ofInstant(
                jwtService.expiryFromToken(token),
                ZoneId.systemDefault()
        ));
        authSessionRepository.save(authSession);

        return new AuthResponse(token, UserResponse.from(user));
    }

    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.getEmail().trim())
                .ifPresent(user -> {
                    byte[] randomBytes = new byte[RESET_TOKEN_BYTES];
                    SECURE_RANDOM.nextBytes(randomBytes);
                    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

                    PasswordResetToken resetToken = new PasswordResetToken();
                    resetToken.setUser(user);
                    resetToken.setTokenHash(jwtService.sha256(token));
                    resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(resetTokenExpirationMinutes));
                    passwordResetTokenRepository.save(resetToken);

                    String separator = passwordResetFrontendUrl.contains("?") ? "&" : "?";
                    String resetUrl = passwordResetFrontendUrl + separator + "token=" + token;
                    passwordResetLinkNotifier.sendResetLink(user.getId(), user.getEmail(), resetUrl);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByTokenHashForUpdate(jwtService.sha256(request.getToken()))
                .orElseThrow(() -> ApiException.badRequest("Đường dẫn đổi mật khẩu không hợp lệ hoặc đã hết hạn."));
        if (resetToken.getUsedAt() != null || !resetToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Đường dẫn đổi mật khẩu không hợp lệ hoặc đã hết hạn.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        resetToken.setUsedAt(LocalDateTime.now());
        authSessionRepository.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản."));
    }

    /** Người dùng gửi yêu cầu trở thành người bán; Admin Console sẽ duyệt/từ chối. */
    @Transactional
    public User requestSellerRole(Long userId) {
        User user = getCurrentUser(userId);
        if (Boolean.TRUE.equals(user.getSeller())) {
            throw ApiException.badRequest("Bạn đã có quyền người bán.");
        }
        if (user.getSellerRequestedAt() != null) {
            throw ApiException.conflict("Yêu cầu đang chờ admin xét duyệt.");
        }
        user.setSellerRequestedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Transactional
    public User updateCurrentUser(Long userId, UpdateProfileRequest request) {
        User user = getCurrentUser(userId);
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        return user;
    }
}
