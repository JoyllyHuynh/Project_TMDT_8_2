package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundCode(String refundCode);

    Optional<Refund> findFirstByOrderOrderByCreatedAtDesc(Order order);
}
