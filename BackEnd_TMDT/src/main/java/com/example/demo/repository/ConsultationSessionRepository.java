package com.example.demo.repository;

import com.example.demo.model.ConsultationSession;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationSessionRepository extends JpaRepository<ConsultationSession, Long> {

    List<ConsultationSession> findByUserOrderByCreatedAtDesc(User user);
}
