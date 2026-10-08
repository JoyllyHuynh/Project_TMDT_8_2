package com.example.demo.repository;

import com.example.demo.model.ExchangeOffer;
import com.example.demo.model.ExchangeRoom;
import com.example.demo.model.User;
import com.example.demo.model.enums.ExchangeOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeOfferRepository extends JpaRepository<ExchangeOffer, Long> {

    List<ExchangeOffer> findByRoomOrderByCreatedAtDesc(ExchangeRoom room);

    List<ExchangeOffer> findByRoomAndBuyer(ExchangeRoom room, User buyer);

    List<ExchangeOffer> findByRoomAndStatus(ExchangeRoom room, ExchangeOfferStatus status);
}
