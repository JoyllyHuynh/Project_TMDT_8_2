package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.catalog.AuthorResponse;
import com.example.demo.dto.catalog.CategoryResponse;
import com.example.demo.dto.catalog.PublisherResponse;
import com.example.demo.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/api/categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getCategories()));
    }

    @GetMapping("/api/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategory(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getCategory(id)));
    }

    @GetMapping("/api/authors")
    public ResponseEntity<ApiResponse<List<AuthorResponse>>> getAuthors(
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getAuthors(keyword)));
    }

    @GetMapping("/api/authors/{id}")
    public ResponseEntity<ApiResponse<AuthorResponse>> getAuthor(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getAuthor(id)));
    }

    @GetMapping("/api/publishers")
    public ResponseEntity<ApiResponse<List<PublisherResponse>>> getPublishers() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublishers()));
    }

    @GetMapping("/api/publishers/{id}")
    public ResponseEntity<ApiResponse<PublisherResponse>> getPublisher(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublisher(id)));
    }
}
