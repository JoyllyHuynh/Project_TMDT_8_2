package com.example.demo.dto.voucher;

import com.example.demo.model.enums.VoucherDiscountType;
import com.example.demo.model.enums.VoucherType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherAvailableResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private VoucherType voucherType;
    private VoucherDiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal minOrderAmount;
    private BigDecimal maxDiscountAmount;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
