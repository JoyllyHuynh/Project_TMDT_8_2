package com.example.demo.repository;

import com.example.demo.model.ConsultationMessage;
import com.example.demo.model.ConsultationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationMessageRepository extends JpaRepository<ConsultationMessage, Long> {

    List<ConsultationMessage> findBySessionOrderByIdAsc(ConsultationSession session);
}
