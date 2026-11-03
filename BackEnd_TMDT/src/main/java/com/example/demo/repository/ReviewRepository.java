package com.example.demo.repository;

import com.example.demo.model.OrderItem;
import com.example.demo.model.Review;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderItem(OrderItem orderItem);

    boolean existsByOrderItem(OrderItem orderItem);

    List<Review> findByBuyerOrderByCreatedAtDesc(User buyer);

    @Query("select review from Review review where review.orderItem.product.id = :productId order by review.createdAt desc")
    List<Review> findByProductId(@Param("productId") Long productId);

    @Query("select review from Review review where review.orderItem.order.seller.id = :sellerId order by review.createdAt desc")
    List<Review> findBySellerId(@Param("sellerId") Long sellerId);

    @Query("select review.rating, count(review) from Review review where review.orderItem.product.id = :productId group by review.rating")
    List<Object[]> ratingDistributionByProductId(@Param("productId") Long productId);
}
