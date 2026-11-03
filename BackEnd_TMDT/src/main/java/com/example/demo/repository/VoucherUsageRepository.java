package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.VoucherUsage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {

    long countByVoucherIdAndUserId(Long voucherId, Long userId);

    boolean existsByVoucherIdAndOrderId(Long voucherId, Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT vu FROM VoucherUsage vu
            WHERE vu.voucher.id = :voucherId AND vu.order.id = :orderId
            """)
    Optional<VoucherUsage> findByVoucherIdAndOrderIdForUpdate(
            @Param("voucherId") Long voucherId,
            @Param("orderId") Long orderId
    );

    List<VoucherUsage> findByOrder(Order order);
}