package com.example.demo.repository;

import com.example.demo.model.CheckoutBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckoutBatchRepository extends JpaRepository<CheckoutBatch, Long> {

    Optional<CheckoutBatch> findByCheckoutCode(String checkoutCode);

    Optional<CheckoutBatch> findByIdempotencyKey(String idempotencyKey);
}
