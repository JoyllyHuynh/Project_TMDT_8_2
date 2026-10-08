package com.example.demo.repository;

import com.example.demo.model.LoyaltyAccount;
import com.example.demo.model.LoyaltyTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByLoyaltyAccountOrderByCreatedAtDesc(LoyaltyAccount account);
}
