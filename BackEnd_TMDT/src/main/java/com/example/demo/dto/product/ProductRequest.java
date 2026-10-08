package com.example.demo.dto.product;

import com.example.demo.model.enums.ListingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private Integer categoryId;

    private Integer authorId;
    private Integer publisherId;
    @Size(max = 100)
    private String volumeNumbers;
    @Size(max = 50)
    private String editionType;

    @Min(1000)
    @Max(3000)
    private Integer publicationYear;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer conditionPercent;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal price;

    @NotNull
    @Min(0)
    private Integer stockQuantity = 1;

    private ListingType listingType = ListingType.SELL;
    private String tradeWishNote;
    @Size(max = 10)
    private List<@NotBlank @Size(max = 2048) String> imageUrls = new ArrayList<>();
}
