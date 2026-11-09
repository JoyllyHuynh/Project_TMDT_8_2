package com.example.demo.dto.voucher;

import com.example.demo.model.enums.ShippingMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VoucherAvailableRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private ShippingMethod shippingMethod = ShippingMethod.STANDARD;
}
