package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrder(Order order);

    @Query("""
            select item from OrderItem item
            where item.order.buyer.id = :buyerId
              and item.order.status in :statuses
              and not exists (select review from Review review where review.orderItem = item)
            order by item.order.createdAt desc, item.id asc
            """)
    List<OrderItem> findReviewableByBuyer(
            @Param("buyerId") Long buyerId,
            @Param("statuses") Collection<OrderStatus> statuses
    );
}
