package com.example.demo.service;

import com.example.demo.dto.product.PageResponse;
import com.example.demo.dto.product.ProductResponse;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatbotToolRegistryTest {

    private ProductService productService;
    private CatalogService catalogService;
    private OrderService orderService;
    private FaqKnowledgeService faqKnowledgeService;
    private ChatbotToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        productService = mock(ProductService.class);
        catalogService = mock(CatalogService.class);
        orderService = mock(OrderService.class);
        faqKnowledgeService = mock(FaqKnowledgeService.class);
        toolRegistry = new ChatbotToolRegistry(
                productService,
                catalogService,
                orderService,
                faqKnowledgeService,
                new ObjectMapper()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void productSearchUsesBoundedPublicCatalogFiltersAndBackendProductLinks() {
        ProductResponse product = ProductResponse.builder()
                .id(44L)
                .title("Naruto - Tập 1")
                .price(new BigDecimal("50000"))
                .stockQuantity(3)
                .conditionPercent(95)
                .imageUrls(List.of())
                .build();
        when(productService.getProducts(
                eq("Naruto"), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(5), eq("price"), eq("asc")
        )).thenReturn(PageResponse.<ProductResponse>builder().content(List.of(product)).build());

        var result = toolRegistry.execute(
                "search_products",
                "{\"keyword\":\"Naruto\",\"sortBy\":\"price\",\"direction\":\"asc\"}"
        );

        assertEquals(1, result.products().size());
        assertEquals(44L, result.products().get(0).id());
        assertEquals("/tin/html/product_details.html?id=44", result.products().get(0).url());
        assertTrue(result.content().contains("50000"));
    }

    @Test
    void rejectsInvalidPriceRangeBeforeQueryingProducts() {
        var result = toolRegistry.execute(
                "search_products",
                "{\"minPrice\":75000,\"maxPrice\":35000}"
        );

        assertTrue(result.content().contains("không hợp lệ"));
        verifyNoInteractions(productService);
    }

    @Test
    void rejectsMalformedFilterTypesInsteadOfDroppingTheFilter() {
        var result = toolRegistry.execute("search_products", "{\"minPrice\":\"cheap\"}");

        assertTrue(result.content().contains("không hợp lệ"));
        verifyNoInteractions(productService);
    }

    @Test
    void unauthenticatedOrderLookupRequiresLoginWithoutCallingOrderService() {
        var result = toolRegistry.execute("lookup_my_orders", "{}");

        assertTrue(result.requiresLogin());
        verifyNoInteractions(orderService);
    }

    @Test
    void authenticatedOrderLookupUsesCurrentPrincipalAndNoCallerSuppliedUserId() {
        User user = new User();
        user.setId(9L);
        user.setEmail("buyer@example.com");
        user.setRole(UserRole.USER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        UserPrincipal principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        when(orderService.getChatbotOrderSummaries()).thenReturn(List.of());

        var result = toolRegistry.execute("lookup_my_orders", "{\"userId\":1}");

        assertEquals("Tài khoản này chưa có đơn hàng.", result.content());
        verify(orderService).getChatbotOrderSummaries();
    }
}
