package com.example.demo.dto.payment;

import com.example.demo.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MockPaymentResponse {

    private Long paymentId;
    private String paymentCode;
    private String paymentMethod;
    private String status;
    private BigDecimal amount;
    private LocalDateTime paidAt;

    public static MockPaymentResponse from(Payment payment) {
        return new MockPaymentResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getMethod().name(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getPaidAt()
        );
    }
}