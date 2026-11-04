package com.example.demo.repository;

import com.example.demo.model.Review;
import com.example.demo.model.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewOrderByDisplayOrderAsc(Review review);

    List<ReviewImage> findByReviewInOrderByDisplayOrderAsc(Collection<Review> reviews);

    void deleteByReview(Review review);
}
