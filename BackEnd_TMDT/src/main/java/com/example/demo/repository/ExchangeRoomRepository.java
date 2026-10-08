package com.example.demo.repository;

import com.example.demo.model.ExchangeRoom;
import com.example.demo.model.User;
import com.example.demo.model.enums.ExchangeRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeRoomRepository extends JpaRepository<ExchangeRoom, Long> {

    Optional<ExchangeRoom> findByRoomCode(String roomCode);

    List<ExchangeRoom> findByHostOrderByCreatedAtDesc(User host);

    List<ExchangeRoom> findByStatusOrderByCreatedAtDesc(ExchangeRoomStatus status);
}
