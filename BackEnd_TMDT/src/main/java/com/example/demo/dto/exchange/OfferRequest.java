package com.example.demo.dto.exchange;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OfferRequest {

    @DecimalMin("0.00")
    private BigDecimal cashCompensation = BigDecimal.ZERO;

    private Long compensationPayerId;
    private String note;

    @Valid
    private List<OfferItemRequest> items = new ArrayList<>();

    @Getter
    @Setter
    public static class OfferItemRequest {
        private Long productId;
        private Integer quantity = 1;
    }
}
