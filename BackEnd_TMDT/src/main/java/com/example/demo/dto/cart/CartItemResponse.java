package com.example.demo.dto.cart;

import com.example.demo.model.CartItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CartItemResponse {

    private Long id;
    private Long productId;
    private String title;
    private BigDecimal price;
    private Integer quantity;
    private Boolean selected;
    private Integer stockQuantity;

    public static CartItemResponse from(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct().getId())
                .title(item.getProduct().getTitle())
                .price(item.getProduct().getPrice())
                .quantity(item.getQuantity())
                .selected(item.getSelected())
                .stockQuantity(item.getProduct().getStockQuantity())
                .build();
    }
}
