package com.example.demo.dto.order;

import com.example.demo.model.enums.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CheckoutRequest {
    @NotNull
    private Long productId;
    @NotNull
    @Min(value = 1)
    private Integer quantity;

    @NotBlank
    private String recipientName;

    @NotBlank
    private String recipientPhone;

    @NotBlank
    private String shippingAddress;

    private String buyerNote;
    private BigDecimal shippingFee = BigDecimal.ZERO;
    private PaymentMethod paymentMethod = PaymentMethod.VNPAY;
    private String idempotencyKey;
}
