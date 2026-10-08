package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    List<OrderStatusHistory> findByOrderOrderByCreatedAtAsc(Order order);
}
