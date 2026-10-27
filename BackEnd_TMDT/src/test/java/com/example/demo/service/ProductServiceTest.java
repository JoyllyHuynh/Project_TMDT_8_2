package com.example.demo.service;

import com.example.demo.exception.ApiException;
import com.example.demo.dto.product.ProductVisibilityRequest;
import com.example.demo.model.Category;
import com.example.demo.model.Product;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PublisherRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private ProductImageRepository productImageRepository;
    private CategoryRepository categoryRepository;
    private UserRepository userRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        productRepository = mock(ProductRepository.class);
        productImageRepository = mock(ProductImageRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        userRepository = mock(UserRepository.class);
        productService = new ProductService(
                productRepository,
                productImageRepository,
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

    @Test
    void ownerCanHideApprovedProductWithoutChangingModeration() {
        Product product = product(15L, ModerationStatus.APPROVED);
        authenticateAs(product.getSeller());
        when(productRepository.findByIdAndDeletedAtIsNull(15L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateVisibility(15L, new ProductVisibilityRequest(false));

        assertEquals(false, product.getActive());
        assertEquals(ModerationStatus.APPROVED, product.getModerationStatus());
        assertEquals(15L, response.getId());
        verify(productRepository).save(product);
    }

    @Test
    void ownerCanRestoreOnlyAnApprovedProduct() {
        Product product = product(16L, ModerationStatus.APPROVED);
        product.setActive(false);
        authenticateAs(product.getSeller());
        when(productRepository.findByIdAndDeletedAtIsNull(16L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        var response = productService.updateVisibility(16L, new ProductVisibilityRequest(true));

        assertEquals(true, product.getActive());
        assertEquals(ModerationStatus.APPROVED, response.getModerationStatus());
        verify(productRepository).save(product);
    }

    @Test
    void doesNotAllowRestoringProductBeforeModerationApproval() {
        Product product = product(17L, ModerationStatus.PENDING);
        product.setActive(false);
        authenticateAs(product.getSeller());
        when(productRepository.findByIdAndDeletedAtIsNull(17L)).thenReturn(Optional.of(product));

        ApiException error = assertThrows(
                ApiException.class,
                () -> productService.updateVisibility(17L, new ProductVisibilityRequest(true))
        );

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, error.getStatus());
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void cannotChangeVisibilityOfAnotherSellersProduct() {
        Product product = product(18L, ModerationStatus.APPROVED);
        User otherSeller = new User();
        otherSeller.setId(99L);
        otherSeller.setEmail("other@example.com");
        otherSeller.setRole(UserRole.USER);
        otherSeller.setAccountStatus(AccountStatus.ACTIVE);
        authenticateAs(otherSeller);
        when(productRepository.findByIdAndDeletedAtIsNull(18L)).thenReturn(Optional.of(product));

        ApiException error = assertThrows(
                ApiException.class,
                () -> productService.updateVisibility(18L, new ProductVisibilityRequest(false))
        );

        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, error.getStatus());
        verifyNoInteractions(productImageRepository);
    }

    @Test
    void rejectsMissingVisibilityAndRequiresAuthentication() {
        ApiException invalid = assertThrows(
                ApiException.class,
                () -> productService.updateVisibility(19L, new ProductVisibilityRequest(null))
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, invalid.getStatus());

        when(productRepository.findByIdAndDeletedAtIsNull(19L))
                .thenReturn(Optional.of(product(19L, ModerationStatus.APPROVED)));
        ApiException unauthorized = assertThrows(
                ApiException.class,
                () -> productService.updateVisibility(19L, new ProductVisibilityRequest(false))
        );
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, unauthorized.getStatus());
        verify(productRepository).findByIdAndDeletedAtIsNull(19L);
        verifyNoInteractions(productImageRepository);
    }

    private Product product(Long id, ModerationStatus moderationStatus) {
        User seller = new User();
        seller.setId(15L);
        seller.setEmail("seller@example.com");
        seller.setFullName("Seller");
        seller.setRole(UserRole.USER);
        seller.setAccountStatus(AccountStatus.ACTIVE);

        Category category = new Category();
        category.setId(2);
        category.setName("Manga");

        Product product = new Product();
        product.setId(id);
        product.setSeller(seller);
        product.setCategory(category);
        product.setTitle("Test product");
        product.setSlug("test-product-" + id);
        product.setDescription("Test description");
        product.setConditionPercent(95);
        product.setPrice(new BigDecimal("50000"));
        product.setStockQuantity(3);
        product.setActive(true);
        product.setModerationStatus(moderationStatus);
        return product;
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
