package com.example.demo.repository;

import com.example.demo.model.Review;
import com.example.demo.model.ReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Long> {

    Optional<ReviewReply> findByReview(Review review);

    List<ReviewReply> findByReviewIn(Collection<Review> reviews);
}
