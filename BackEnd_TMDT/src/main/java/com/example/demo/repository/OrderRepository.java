package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);

    List<Order> findBySellerOrderByCreatedAtDesc(User seller);

    List<Order> findBySellerAndStatusOrderByCreatedAtDesc(User seller, OrderStatus status);
}
