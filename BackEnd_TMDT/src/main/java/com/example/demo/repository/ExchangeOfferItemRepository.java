package com.example.demo.repository;

import com.example.demo.model.ExchangeOffer;
import com.example.demo.model.ExchangeOfferItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExchangeOfferItemRepository extends JpaRepository<ExchangeOfferItem, Long> {

    List<ExchangeOfferItem> findByOffer(ExchangeOffer offer);

    void deleteByOffer(ExchangeOffer offer);
}
