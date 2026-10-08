package com.example.demo.service;

public interface PasswordResetLinkNotifier {

    void sendResetLink(Long userId, String email, String resetUrl);
}
