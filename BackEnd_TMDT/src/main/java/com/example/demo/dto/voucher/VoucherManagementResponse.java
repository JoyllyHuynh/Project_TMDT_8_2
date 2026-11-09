package com.example.demo.dto.voucher;

import com.example.demo.model.enums.VoucherDiscountType;
import com.example.demo.model.enums.VoucherType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherManagementResponse {

    private Long id;
    private String code;
    private String name;
    private String description;

    private VoucherType voucherType;
    private VoucherDiscountType discountType;

    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;

    private Long sellerId;
    private String sellerName;

    private Integer usageLimit;
    private Integer usedCount;
    private Integer usageLimitPerUser;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}