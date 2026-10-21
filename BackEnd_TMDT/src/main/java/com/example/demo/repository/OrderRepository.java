package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    List<Order> findByBuyerOrderByCreatedAtDesc(User buyer);
    List<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId);

    List<Order> findBySellerOrderByCreatedAtDesc(User seller);

    List<Order> findBySellerAndStatusOrderByCreatedAtDesc(User seller, OrderStatus status);

    /**
     * Toàn bộ đơn hàng của sàn cho Admin Console (kèm tên người mua / người bán).
     */
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN o.buyer b
            LEFT JOIN o.seller s
            WHERE (:q IS NULL
                   OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.email) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR o.status = :status)
            ORDER BY o.createdAt DESC
            """)
    Page<Order> search(@Param("q") String q, @Param("status") OrderStatus status, Pageable pageable);

    /** Đơn đang tranh chấp: đã mở nhưng chưa có kết quả hoà giải. */
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN o.buyer b
            LEFT JOIN o.seller s
            WHERE o.disputedAt IS NOT NULL AND o.disputeResolvedAt IS NULL
              AND (:q IS NULL
                   OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(b.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY o.disputedAt DESC
            """)
    Page<Order> searchDisputed(@Param("q") String q, Pageable pageable);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupedByStatus();

    /** Tổng hợp số tiền/giao dịch mà từng người đã chi (dùng cho màn Quản lý tài khoản). */
    @Query("SELECT o.buyer.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o GROUP BY o.buyer.id")
    List<Object[]> totalSpentByBuyer();

    /** Tổng hợp số tiền/giao dịch mà từng người đã bán. */
    @Query("SELECT o.seller.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o GROUP BY o.seller.id")
    List<Object[]> totalSoldBySeller();

    long countByDisputedAtIsNotNullAndDisputeResolvedAtIsNull();

    @Query("SELECT o.buyer.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.buyer.id IN (:userIds) GROUP BY o.buyer.id")
    List<Object[]> totalSpentByBuyerInIds(@Param("userIds") List<Long> userIds);

    @Query("SELECT o.seller.id, COUNT(o), COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.seller.id IN (:userIds) GROUP BY o.seller.id")
    List<Object[]> totalSoldBySellerInIds(@Param("userIds") List<Long> userIds);
}
