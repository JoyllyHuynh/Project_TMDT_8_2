package com.example.demo.controller;

import com.example.demo.dto.product.PageResponse;
import com.example.demo.dto.product.ProductResponse;
import com.example.demo.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerSortingTest {

    private ProductService productService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService)).build();
        when(productService.getProducts(
                any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()
        )).thenReturn(PageResponse.<ProductResponse>builder()
                .content(List.of())
                .page(0)
                .pageSize(5)
                .totalElements(0)
                .totalPages(0)
                .build());
    }

    @Test
    void sortByAliasPassesAscendingPriceSortAndPreservesFilters() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sortBy", "price")
                        .param("direction", "asc")
                        .param("page", "0")
                        .param("pageSize", "5")
                        .param("keyword", "Naruto")
                        .param("categoryId", "3")
                        .param("minPrice", "35000")
                        .param("maxPrice", "80000"))
                .andExpect(status().isOk());

        var sort = ArgumentCaptor.forClass(String.class);
        var direction = ArgumentCaptor.forClass(String.class);
        var keyword = ArgumentCaptor.forClass(String.class);
        var categoryId = ArgumentCaptor.forClass(Integer.class);
        var minPrice = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        var maxPrice = ArgumentCaptor.forClass(java.math.BigDecimal.class);
        var page = ArgumentCaptor.forClass(Integer.class);
        var pageSize = ArgumentCaptor.forClass(Integer.class);
        verify(productService).getProducts(
                keyword.capture(), categoryId.capture(), minPrice.capture(), maxPrice.capture(),
                any(), any(), any(), any(), page.capture(), pageSize.capture(),
                sort.capture(), direction.capture()
        );

        assertEquals("price", sort.getValue());
        assertEquals("asc", direction.getValue());
        assertEquals("Naruto", keyword.getValue());
        assertEquals(3, categoryId.getValue());
        assertEquals(new java.math.BigDecimal("35000"), minPrice.getValue());
        assertEquals(new java.math.BigDecimal("80000"), maxPrice.getValue());
        assertEquals(0, page.getValue());
        assertEquals(5, pageSize.getValue());
    }

    @Test
    void sortByAliasPassesDescendingPriceSort() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sortBy", "price")
                        .param("direction", "desc")
                        .param("page", "0")
                        .param("pageSize", "5"))
                .andExpect(status().isOk());

        var sort = ArgumentCaptor.forClass(String.class);
        var direction = ArgumentCaptor.forClass(String.class);
        verify(productService).getProducts(
                any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), sort.capture(), direction.capture()
        );

        assertEquals("price", sort.getValue());
        assertEquals("desc", direction.getValue());
    }

    @Test
    void legacySortParameterRemainsSupported() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sort", "price")
                        .param("direction", "asc"))
                .andExpect(status().isOk());

        var sort = ArgumentCaptor.forClass(String.class);
        var direction = ArgumentCaptor.forClass(String.class);
        verify(productService).getProducts(
                any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), sort.capture(), direction.capture()
        );

        assertEquals("price", sort.getValue());
        assertEquals("asc", direction.getValue());
    }
}
