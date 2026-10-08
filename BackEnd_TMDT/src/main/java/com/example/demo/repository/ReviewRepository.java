package com.example.demo.repository;

import com.example.demo.model.OrderItem;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderItem(OrderItem orderItem);

    List<Review> findByBuyerOrderByCreatedAtDesc(User buyer);
}
