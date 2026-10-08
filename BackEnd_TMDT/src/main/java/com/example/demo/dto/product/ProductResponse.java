package com.example.demo.dto.product;

import com.example.demo.model.Product;
import com.example.demo.model.ProductImage;
import com.example.demo.model.enums.ListingType;
import com.example.demo.model.enums.ModerationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProductResponse {

    private Long id;
    private Long sellerId;
    private String sellerName;
    private String title;
    private String slug;
    private String description;
    private Integer categoryId;
    private String categoryName;
    private Integer authorId;
    private String authorName;
    private Integer publisherId;
    private String publisherName;
    private String volumeNumbers;
    private String editionType;
    private Integer publicationYear;
    private Integer conditionPercent;
    private BigDecimal price;
    private Integer stockQuantity;
    private ListingType listingType;
    private String tradeWishNote;
    private Boolean active;
    private ModerationStatus moderationStatus;
    private LocalDateTime createdAt;
    private List<String> imageUrls;
    private List<ProductImageResponse> images;

    public static ProductResponse from(Product product, List<ProductImage> images) {
        return ProductResponse.builder()
                .id(product.getId())
                .sellerId(product.getSeller().getId())
                .sellerName(product.getSeller().getFullName())
                .title(product.getTitle())
                .slug(product.getSlug())
                .description(product.getDescription())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .authorId(product.getAuthor() != null ? product.getAuthor().getId() : null)
                .authorName(product.getAuthor() != null ? product.getAuthor().getName() : null)
                .publisherId(product.getPublisher() != null ? product.getPublisher().getId() : null)
                .publisherName(product.getPublisher() != null ? product.getPublisher().getName() : null)
                .volumeNumbers(product.getVolumeNumbers())
                .editionType(product.getEditionType())
                .publicationYear(product.getPublicationYear())
                .conditionPercent(product.getConditionPercent())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .listingType(product.getListingType())
                .tradeWishNote(product.getTradeWishNote())
                .active(product.getActive())
                .moderationStatus(product.getModerationStatus())
                .createdAt(product.getCreatedAt())
                .imageUrls(images.stream().map(ProductImage::getImageUrl).toList())
                .images(images.stream().map(ProductImageResponse::from).toList())
                .build();
    }
}
