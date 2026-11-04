package com.example.demo.repository;

import com.example.demo.model.Notification;
import com.example.demo.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    @Query("""
            SELECT notification FROM Notification notification
            JOIN FETCH notification.order
            WHERE notification.user.id = :userId
            ORDER BY notification.createdAt DESC
            """)
    List<Notification> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);
}
