package com.example.demo;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String password = "TestUser2026!";

        System.out.println("BCrypt Hash:");
        System.out.println(encoder.encode(password));
    }
}