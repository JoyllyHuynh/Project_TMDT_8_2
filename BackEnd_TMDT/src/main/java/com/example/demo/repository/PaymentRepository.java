package com.example.demo.repository;

import com.example.demo.model.CheckoutBatch;
import com.example.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentCode(String paymentCode);

    List<Payment> findByCheckout(CheckoutBatch checkout);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p
            WHERE p.id = :id
            """)
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Payment p
            WHERE p.checkout.id = :checkoutId
            """)
    List<Payment> findByCheckoutIdForUpdate(
            @Param("checkoutId") Long checkoutId
    );
}
