package com.example.demo.repository;

import com.example.demo.model.PaymentAllocation;
import com.example.demo.model.id.PaymentAllocationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, PaymentAllocationId> {
}
