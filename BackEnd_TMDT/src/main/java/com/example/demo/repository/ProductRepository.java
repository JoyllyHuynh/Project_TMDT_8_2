package com.example.demo.repository;

import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.model.enums.ListingType;
import com.example.demo.model.enums.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    Optional<Product> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlug(String slug);

    List<Product> findBySellerAndDeletedAtIsNullOrderByCreatedAtDesc(User seller);

    long countByDeletedAtIsNull();

    long countByModerationStatusAndDeletedAtIsNull(ModerationStatus moderationStatus);

    long countByActiveAndDeletedAtIsNull(Boolean active);

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.author author
            LEFT JOIN p.publisher publisher
            LEFT JOIN p.seller seller
            WHERE p.deletedAt IS NULL
              AND (:active IS NULL OR p.active = :active)
              AND (:moderationStatus IS NULL OR p.moderationStatus = :moderationStatus)
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:listingType IS NULL OR p.listingType = :listingType)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:minCondition IS NULL OR p.conditionPercent >= :minCondition)
              AND (:maxCondition IS NULL OR p.conditionPercent <= :maxCondition)
              AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(p.category.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(author.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(publisher.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:sellerName IS NULL OR LOWER(seller.fullName) LIKE LOWER(CONCAT('%', :sellerName, '%')))
            """)
    Page<Product> search(
            @Param("q") String q,
            @Param("categoryId") Integer categoryId,
            @Param("listingType") ListingType listingType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minCondition") Integer minCondition,
            @Param("maxCondition") Integer maxCondition,
            @Param("sellerName") String sellerName,
            @Param("active") Boolean active,
            @Param("moderationStatus") ModerationStatus moderationStatus,
            Pageable pageable
    );
}
