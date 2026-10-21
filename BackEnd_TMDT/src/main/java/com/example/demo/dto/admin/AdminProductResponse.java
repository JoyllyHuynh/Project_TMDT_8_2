package com.example.demo.dto.admin;

import com.example.demo.dto.product.ProductImageResponse;
import com.example.demo.model.Product;
import com.example.demo.model.ProductImage;
import com.example.demo.model.enums.ListingType;
import com.example.demo.model.enums.ModerationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Dữ liệu sản phẩm hiển thị trên Admin Console (Quản lý sản phẩm & kiểm duyệt).
 */
@Getter
@Builder
public class AdminProductResponse {

    private Long id;
    private String title;
    private String slug;
    private String description;
    private Long sellerId;
    private String sellerName;
    private Integer categoryId;
    private String categoryName;
    private String authorName;
    private String publisherName;
    private Integer conditionPercent;
    private BigDecimal price;
    private Integer stockQuantity;
    private ListingType listingType;
    private Boolean active;
    private ModerationStatus moderationStatus;
    private String moderationReason;
    private LocalDateTime moderatedAt;
    private LocalDateTime createdAt;
    private List<String> imageUrls;
    private List<ProductImageResponse> images;

    public static AdminProductResponse from(Product product, List<ProductImage> images) {
        return AdminProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .slug(product.getSlug())
                .description(product.getDescription())
                .sellerId(product.getSeller().getId())
                .sellerName(product.getSeller().getFullName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .authorName(product.getAuthor() != null ? product.getAuthor().getName() : null)
                .publisherName(product.getPublisher() != null ? product.getPublisher().getName() : null)
                .conditionPercent(product.getConditionPercent())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .listingType(product.getListingType())
                .active(product.getActive())
                .moderationStatus(product.getModerationStatus())
                .moderationReason(product.getModerationReason())
                .moderatedAt(product.getModeratedAt())
                .createdAt(product.getCreatedAt())
                .imageUrls(images.stream().map(ProductImage::getImageUrl).toList())
                .images(images.stream().map(ProductImageResponse::from).toList())
                .build();
    }
}
