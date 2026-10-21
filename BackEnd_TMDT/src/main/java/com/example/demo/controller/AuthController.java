package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.auth.AuthResponse;
import com.example.demo.dto.auth.ForgotPasswordRequest;
import com.example.demo.dto.auth.LoginRequest;
import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.dto.auth.ResetPasswordRequest;
import com.example.demo.dto.auth.UpdateProfileRequest;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng ký thành công", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", authService.login(request)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email được đăng ký, yêu cầu đặt lại mật khẩu đã được tiếp nhận.",
                null
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Mật khẩu đã được cập nhật.", null));
    }

    @PostMapping("/me/seller-request")
    public ResponseEntity<ApiResponse<UserResponse>> requestSellerRole() {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi yêu cầu trở thành người bán, chờ admin xét duyệt.",
                UserResponse.from(authService.requestSellerRole(SecurityUtils.currentUserId()))
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> currentUser() {
        return ResponseEntity.ok(ApiResponse.success(
                UserResponse.from(authService.getCurrentUser(SecurityUtils.currentUserId()))
        ));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật hồ sơ thành công.",
                UserResponse.from(authService.updateCurrentUser(SecurityUtils.currentUserId(), request))
        ));
    }
}
