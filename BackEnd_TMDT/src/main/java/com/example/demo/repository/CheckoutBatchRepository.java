
package com.example.demo.repository;

import com.example.demo.model.CheckoutBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.demo.model.enums.CheckoutBatchStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutBatchRepository extends JpaRepository<CheckoutBatch, Long> {

    Optional<CheckoutBatch> findByCheckoutCode(String checkoutCode);

    Optional<CheckoutBatch> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM CheckoutBatch c
            WHERE c.idempotencyKey = :key
            """)
    Optional<CheckoutBatch> findByIdempotencyKeyForUpdate(
            @Param("key") String key
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM CheckoutBatch c
            WHERE c.id = :id
            """)
    Optional<CheckoutBatch> findByIdForUpdate(@Param("id") Long id);

    List<CheckoutBatch> findByStatusAndExpiresAtLessThanEqual(
        CheckoutBatchStatus status,
        LocalDateTime now
    );
}
