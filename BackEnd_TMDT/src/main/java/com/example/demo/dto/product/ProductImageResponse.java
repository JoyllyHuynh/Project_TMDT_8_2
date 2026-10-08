package com.example.demo.dto.product;

import com.example.demo.model.ProductImage;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductImageResponse {

    private String imageUrl;
    private Integer displayOrder;

    public static ProductImageResponse from(ProductImage image) {
        return ProductImageResponse.builder()
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .build();
    }
}
