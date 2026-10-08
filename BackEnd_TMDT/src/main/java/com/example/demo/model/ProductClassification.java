package com.example.demo.model;

import com.example.demo.model.enums.ClassificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_classifications")
public class ProductClassification {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassificationStatus status = ClassificationStatus.PENDING;

    @Column(name = "is_comic")
    private Boolean comic;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "classified_at")
    private LocalDateTime classifiedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
