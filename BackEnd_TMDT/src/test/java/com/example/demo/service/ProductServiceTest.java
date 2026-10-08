package com.example.demo.service;

import com.example.demo.exception.ApiException;
import com.example.demo.model.Product;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PublisherRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private UserRepository userRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        userRepository = mock(UserRepository.class);
        productService = new ProductService(
                productRepository,
                mock(ProductImageRepository.class),
                categoryRepository,
                mock(AuthorRepository.class),
                mock(PublisherRepository.class),
                userRepository
        );
    }

    @Test
    void publicProductListUsesApprovedActiveFilterAndWhitelistedSort() {
        when(productRepository.search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenAnswer(invocation -> Page.empty(invocation.getArgument(10)));

        var result = productService.getProducts(
                null, null, null, null, null, null, null, null, 0, 12, "newest", "desc"
        );

        assertEquals(0, result.getTotalElements());
        assertEquals(12, result.getPageSize());
        var active = org.mockito.ArgumentCaptor.forClass(Boolean.class);
        var moderationStatus = org.mockito.ArgumentCaptor.forClass(ModerationStatus.class);
        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).search(
                any(), any(), any(), any(), any(), any(), any(), any(), active.capture(),
                moderationStatus.capture(), pageable.capture()
        );
        assertTrue(active.getValue());
        assertEquals(ModerationStatus.APPROVED, moderationStatus.getValue());
        assertEquals(Sort.Direction.DESC, pageable.getValue().getSort().getOrderFor("createdAt").getDirection());
        assertEquals(Sort.Direction.ASC, pageable.getValue().getSort().getOrderFor("id").getDirection());
    }

    @Test
    void rejectsUnknownSortAndInvalidPriceRangeBeforeQuery() {
        assertThrows(ApiException.class, () -> productService.getProducts(
                null, null, null, null, null, null, null, null, 0, 12, "price;delete", "desc"
        ));
        assertThrows(ApiException.class, () -> productService.getProducts(
                null, null, new BigDecimal("10"), new BigDecimal("5"),
                null, null, null, null, 0, 12, "newest", "desc"
        ));

        verifyNoInteractions(productRepository);
    }

    @Test
    void appliesPriceSortDirectionToRepositoryPageable() {
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(productRepository.search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenAnswer(invocation -> Page.empty(invocation.getArgument(10)));

        productService.getProducts(
                "Naruto", 3, new BigDecimal("35000"), new BigDecimal("80000"),
                null, null, null, null, 1, 5, "price", "asc"
        );
        productService.getProducts(
                "Naruto", 3, new BigDecimal("35000"), new BigDecimal("80000"),
                null, null, null, null, 1, 5, "price", "desc"
        );

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository, times(2)).search(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), pageable.capture()
        );
        var pages = pageable.getAllValues();

        assertEquals(Sort.Direction.ASC, pages.get(0).getSort().getOrderFor("price").getDirection());
        assertEquals(Sort.Direction.DESC, pages.get(1).getSort().getOrderFor("price").getDirection());
        assertEquals(1, pages.get(0).getPageNumber());
        assertEquals(5, pages.get(0).getPageSize());
        assertEquals(1, pages.get(1).getPageNumber());
        assertEquals(5, pages.get(1).getPageSize());
    }

    @Test
    void doesNotExposePendingProductDetails() {
        Product product = new Product();
        product.setActive(true);
        product.setModerationStatus(ModerationStatus.PENDING);
        when(productRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(product));

        assertThrows(ApiException.class, () -> productService.getProduct(7L));
    }

    @Test
    void privateSellerListingRequiresAuthentication() {
        assertThrows(ApiException.class, () -> productService.getMyProducts());
        verifyNoInteractions(productRepository, userRepository);
    }
}
