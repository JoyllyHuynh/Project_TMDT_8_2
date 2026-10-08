package com.example.demo.repository;

import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    List<Payment> findByCheckout(CheckoutBatch checkout);
}
