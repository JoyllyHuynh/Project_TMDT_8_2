package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.product.PageResponse;
import com.example.demo.dto.product.ProductRequest;
import com.example.demo.dto.product.ProductResponse;
import com.example.demo.model.enums.ListingType;
import com.example.demo.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minCondition,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maxCondition,
            @RequestParam(required = false) String seller,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProducts(
                keyword,
                categoryId,
                minPrice,
                maxPrice,
                minCondition,
                maxCondition,
                seller,
                listingType,
                page,
                pageSize,
                sortBy != null ? sortBy : sort,
                direction
        )));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minCondition,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maxCondition,
            @RequestParam(required = false) String seller,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return getProducts(
                keyword, categoryId, minPrice, maxPrice, minCondition, maxCondition, seller,
                listingType, page, pageSize, sortBy, sort, direction
        );
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProductsByCategory(
            @PathVariable Integer categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minCondition,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maxCondition,
            @RequestParam(required = false) String seller,
            @RequestParam(required = false) ListingType listingType,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return getProducts(
                keyword, categoryId, minPrice, maxPrice, minCondition, maxCondition, seller,
                listingType, page, pageSize, sortBy, sort, direction
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(id)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getMyProducts() {
        return ResponseEntity.ok(ApiResponse.success(productService.getMyProducts()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Sản phẩm đã được gửi kiểm duyệt.", productService.createProduct(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Sản phẩm đã được cập nhật và gửi kiểm duyệt.",
                productService.updateProduct(id, request)
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sản phẩm.", null));
    }
}
