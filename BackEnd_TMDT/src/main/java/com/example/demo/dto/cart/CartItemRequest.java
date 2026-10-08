package com.example.demo.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartItemRequest {

    @NotNull
    private Long productId;

    @Min(1)
    private Integer quantity = 1;

    private Boolean selected = true;
}
