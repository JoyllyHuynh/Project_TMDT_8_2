
package com.example.demo.dto.voucher;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.example.demo.model.enums.ShippingMethod;
import java.math.BigDecimal;

@Getter
@Setter
public class VoucherValidateRequest {

    @NotNull
    private Long productId;
    private ShippingMethod shippingMethod = ShippingMethod.STANDARD;
    @NotNull
    @Min(1)
    private Integer quantity;

    private String platformCode;

    private String sellerCode;

    private String shippingCode;
}
