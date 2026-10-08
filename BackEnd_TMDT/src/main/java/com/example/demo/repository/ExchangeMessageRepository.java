package com.example.demo.repository;

import com.example.demo.model.ExchangeMessage;
import com.example.demo.model.ExchangeRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExchangeMessageRepository extends JpaRepository<ExchangeMessage, Long> {

    List<ExchangeMessage> findByRoomOrderByIdAsc(ExchangeRoom room);

    Optional<ExchangeMessage> findByClientMessageKey(String clientMessageKey);
}
