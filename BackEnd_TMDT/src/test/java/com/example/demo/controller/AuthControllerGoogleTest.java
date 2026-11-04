package com.example.demo.controller;

import com.example.demo.dto.auth.AuthResponse;
import com.example.demo.dto.auth.UserResponse;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerGoogleTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    void googleLoginAcceptsIdTokenAndReturnsExistingAuthResponseShape() throws Exception {
        UserResponse user = UserResponse.builder()
                .id(42L)
                .email("google.user@example.com")
                .fullName("Google User")
                .role(UserRole.USER)
                .seller(false)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        when(authService.loginWithGoogle("verified-id-token", true))
                .thenReturn(new AuthResponse("comicworm-jwt", user));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content("""
                                {"idToken":"verified-id-token","remember":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.token").value("comicworm-jwt"))
                .andExpect(jsonPath("$.data.user.email").value("google.user@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        verify(authService).loginWithGoogle("verified-id-token", true);
    }
}
