package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.PaymentAllocation;
import com.example.demo.model.id.PaymentAllocationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, PaymentAllocationId> {

    List<PaymentAllocation> findByOrder(Order order);
}
