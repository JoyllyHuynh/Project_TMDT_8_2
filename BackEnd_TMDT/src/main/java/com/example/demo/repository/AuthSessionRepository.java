package com.example.demo.repository;

import com.example.demo.model.AuthSession;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    Optional<AuthSession> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
