package com.example.demo.service;

import com.example.demo.dto.review.ProductReviewsResponse;
import com.example.demo.dto.review.ReviewReplyRequest;
import com.example.demo.dto.review.ReviewRequest;
import com.example.demo.dto.review.ReviewResponse;
import com.example.demo.dto.review.ReviewableItemResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.Review;
import com.example.demo.model.ReviewImage;
import com.example.demo.model.ReviewReply;
import com.example.demo.model.User;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ReviewImageRepository;
import com.example.demo.repository.ReviewReplyRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_IMAGES = 5;
    private static final int MAX_IMAGE_URL_LENGTH = 500;
    private static final Set<OrderStatus> REVIEWABLE_STATUSES = EnumSet.of(
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    private final ReviewRepository reviewRepository;
    private final ReviewReplyRepository reviewReplyRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ReviewableItemResponse> getReviewableItems() {
        User buyer = currentUser();
        return orderItemRepository.findReviewableByBuyer(buyer.getId(), REVIEWABLE_STATUSES).stream()
                .map(ReviewableItemResponse::from)
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(ReviewRequest request) {
        User buyer = currentUser();
        OrderItem item = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm trong đơn hàng."));
        Order order = item.getOrder();

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw ApiException.forbidden("Bạn chỉ có thể đánh giá sản phẩm mình đã mua.");
        }
        if (!REVIEWABLE_STATUSES.contains(order.getStatus())) {
            throw ApiException.conflict("Chỉ có thể đánh giá sau khi đơn hàng đã được giao.");
        }
        if (item.getProduct().getSeller().getId().equals(buyer.getId())) {
            throw ApiException.forbidden("Bạn không thể đánh giá sản phẩm của chính mình.");
        }
        if (reviewRepository.existsByOrderItem(item)) {
            throw ApiException.conflict("Bạn đã đánh giá sản phẩm này rồi.");
        }

        List<String> imageUrls = normalizeImageUrls(request.getImageUrls());

        Review review = new Review();
        review.setOrderItem(item);
        review.setBuyer(buyer);
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());
        Review saved = reviewRepository.save(review);

        for (int index = 0; index < imageUrls.size(); index++) {
            ReviewImage image = new ReviewImage();
            image.setReview(saved);
            image.setImageUrl(imageUrls.get(index));
            image.setDisplayOrder(index);
            reviewImageRepository.save(image);
        }

        return ReviewResponse.from(saved, null, imageUrls);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getMyReviews() {
        User buyer = currentUser();
        return toResponses(reviewRepository.findByBuyerOrderByCreatedAtDesc(buyer));
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getSellerReviews() {
        User seller = currentUser();
        return toResponses(reviewRepository.findBySellerId(seller.getId()));
    }

    @Transactional
    public ReviewResponse reply(Long reviewId, ReviewReplyRequest request) {
        User seller = currentUser();
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đánh giá."));

        if (seller.getRole() != UserRole.ADMIN
                && !review.getOrderItem().getOrder().getSeller().getId().equals(seller.getId())) {
            throw ApiException.forbidden("Chỉ người bán của đơn hàng mới được phản hồi đánh giá.");
        }
        if (reviewReplyRepository.findByReview(review).isPresent()) {
            throw ApiException.conflict("Đánh giá này đã được phản hồi.");
        }

        ReviewReply reply = new ReviewReply();
        reply.setReview(review);
        reply.setSeller(seller);
        reply.setContent(request.getContent().trim());
        ReviewReply saved = reviewReplyRepository.save(reply);

        List<String> images = reviewImageRepository.findByReviewOrderByDisplayOrderAsc(review).stream()
                .map(ReviewImage::getImageUrl)
                .toList();
        return ReviewResponse.from(review, saved, images);
    }

    @Transactional(readOnly = true)
    public ProductReviewsResponse getProductReviews(Long productId) {
        productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));

        List<Review> reviews = reviewRepository.findByProductId(productId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        for (Object[] row : reviewRepository.ratingDistributionByProductId(productId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        double average = reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return ProductReviewsResponse.builder()
                .averageRating(Math.round(average * 10.0) / 10.0)
                .totalReviews((long) reviews.size())
                .distribution(distribution)
                .reviews(toResponses(reviews))
                .build();
    }

    private List<ReviewResponse> toResponses(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }

        Map<Long, ReviewReply> repliesByReview = new HashMap<>();
        for (ReviewReply reply : reviewReplyRepository.findByReviewIn(reviews)) {
            repliesByReview.put(reply.getReview().getId(), reply);
        }

        Map<Long, List<String>> imagesByReview = new HashMap<>();
        for (ReviewImage image : reviewImageRepository.findByReviewInOrderByDisplayOrderAsc(reviews)) {
            imagesByReview.computeIfAbsent(image.getReview().getId(), key -> new ArrayList<>())
                    .add(image.getImageUrl());
        }

        return reviews.stream()
                .map(review -> ReviewResponse.from(
                        review,
                        repliesByReview.get(review.getId()),
                        imagesByReview.getOrDefault(review.getId(), List.of())
                ))
                .toList();
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String value : imageUrls) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String url = value.trim();
            if (url.length() > MAX_IMAGE_URL_LENGTH
                    || !(url.startsWith("http://") || url.startsWith("https://"))) {
                throw ApiException.badRequest("Đường dẫn ảnh không hợp lệ.");
            }
            if (!normalized.contains(url)) {
                normalized.add(url);
            }
        }
        if (normalized.size() > MAX_IMAGES) {
            throw ApiException.badRequest("Mỗi đánh giá tối đa 5 ảnh.");
        }
        return normalized;
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.currentUserId())
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.unauthorized("Bạn cần đăng nhập."));
    }
}
