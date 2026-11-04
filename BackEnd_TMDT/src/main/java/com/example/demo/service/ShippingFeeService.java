
package com.example.demo.service;

import com.example.demo.model.enums.ShippingMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ShippingFeeService {

    private static final BigDecimal STANDARD_FEE =
            new BigDecimal("20000");

    private static final BigDecimal FAST_FEE =
            new BigDecimal("35000");

    public BigDecimal calculateShippingFee(ShippingMethod shippingMethod) {

        if (shippingMethod == null) {
            shippingMethod = ShippingMethod.STANDARD;
        }

        return switch (shippingMethod) {
            case STANDARD -> STANDARD_FEE;
            case FAST -> FAST_FEE;
        };
    }
}
