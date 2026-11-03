package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.review.ReviewReplyRequest;
import com.example.demo.dto.review.ReviewRequest;
import com.example.demo.dto.review.ReviewResponse;
import com.example.demo.dto.review.ReviewableItemResponse;
import com.example.demo.service.ReviewImageStorage;
import com.example.demo.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewImageStorage reviewImageStorage;

    @GetMapping("/reviewable")
    public ResponseEntity<ApiResponse<List<ReviewableItemResponse>>> getReviewableItems() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewableItems()));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getMyReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getMyReviews()));
    }

    @GetMapping("/seller")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getSellerReviews() {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getSellerReviews()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(@Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đánh giá đã được gửi.", reviewService.createReview(request)));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> reply(
            @PathVariable Long id,
            @Valid @RequestBody ReviewReplyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi phản hồi đánh giá.", reviewService.reply(id, request)));
    }

    @PostMapping("/images")
    public ResponseEntity<ApiResponse<String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String path = reviewImageStorage.store(file);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(path).toUriString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ảnh đã được tải lên.", url));
    }
}
