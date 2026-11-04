
package com.example.demo.dto.voucher;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class VoucherValidateResponse {

    private BigDecimal subtotalAmount;

    private BigDecimal shippingFee;

    private BigDecimal platformDiscount;

    private BigDecimal sellerDiscount;

    private BigDecimal shippingDiscount;

    private BigDecimal totalDiscount;

    private BigDecimal totalAmount;
}
