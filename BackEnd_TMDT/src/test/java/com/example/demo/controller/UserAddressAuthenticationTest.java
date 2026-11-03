package com.example.demo.controller;

import com.example.demo.config.SecurityConfig;
import com.example.demo.dto.voucher.VoucherValidateResponse;
import com.example.demo.model.AuthSession;
import com.example.demo.model.User;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthSessionRepository;
import com.example.demo.security.CustomUserDetailsService;
import com.example.demo.security.JwtAuthFilter;
import com.example.demo.security.JwtService;
import com.example.demo.security.UserPrincipal;
import com.example.demo.service.UserAddressService;
import com.example.demo.service.VoucherService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserAddressController.class, VoucherController.class})
@Import(SecurityConfig.class)
class UserAddressAuthenticationTest {

    private static final String TOKEN = "test.jwt.token";
    private static final String EMAIL = "user@example.com";
    private static final String TOKEN_HASH = "b".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private AuthSessionRepository authSessionRepository;

    @MockBean
    private UserAddressService addressService;

    @MockBean
    private VoucherService voucherService;

    @Test
    void jwtFilterIsNotRegisteredOutsideSpringSecurityChain() {
        assertFalse(jwtAuthFilterRegistration.isEnabled());
    }

    @Test
    void rejectsAddressRequestWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập."));
    }

    @Test
    void acceptsValidJwtForVoucherPreviewRequest() throws Exception {
        configureValidAuthentication();
        when(voucherService.validateVouchers(any()))
                .thenReturn(new VoucherValidateResponse());

        mockMvc.perform(post("/api/vouchers/validate")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(voucherService).validateVouchers(any());
    }

    @Test
    void rejectsVoucherPreviewWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/vouchers/validate")
                        .contentType(APPLICATION_JSON)
                        .content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập."));
    }

    @Test
    void acceptsValidJwtWithActiveSessionForProtectedAddressRequest() throws Exception {
        configureValidAuthentication();
        when(addressService.getMyAddresses(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void rejectsInvalidJwtForProtectedAddressRequest() throws Exception {
        when(jwtService.parse(TOKEN)).thenThrow(new MalformedJwtException("invalid token"));

        mockMvc.perform(get("/api/addresses").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    void deniesUserRoleOnAdminEndpoint() throws Exception {
        configureValidAuthentication();

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.ok").value(false));
    }

    private void configureValidAuthentication() {
        Claims claims = mock(Claims.class);
        when(jwtService.parse(TOKEN)).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn(EMAIL);
        when(jwtService.sha256(TOKEN)).thenReturn(TOKEN_HASH);
        when(authSessionRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                eq(TOKEN_HASH),
                any(LocalDateTime.class)
        )).thenReturn(Optional.of(new AuthSession()));

        User user = new User();
        user.setId(5L);
        user.setEmail(EMAIL);
        user.setRole(UserRole.USER);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(new UserPrincipal(user));
    }
}
