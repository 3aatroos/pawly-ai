package com.pawly.service.store;

import com.pawly.dto.store.ReviewDTO;
import com.pawly.entity.*;
import com.pawly.exception.ResourceNotFoundException;
import com.pawly.repository.*;
import com.pawly.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    public Page<ReviewDTO> findByProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(ReviewDTO::from);
    }

    @Transactional
    public ReviewDTO create(Long productId, ReviewDTO dto, String callerEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        User user = userService.getUserByEmail(callerEmail);

        if (reviewRepository.existsByProductIdAndUserId(productId, user.getId())) {
            throw new IllegalStateException("You have already reviewed this product");
        }

        ProductReview review = ProductReview.builder()
                .product(product)
                .user(user)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();

        ReviewDTO saved = ReviewDTO.from(reviewRepository.save(review));
        productRepository.refreshRating(productId);
        return saved;
    }

    @Transactional
    public ReviewDTO update(Long productId, Long reviewId, ReviewDTO dto, String callerEmail, boolean isAdmin) {
        ProductReview review = getReviewForWrite(reviewId, callerEmail, isAdmin);

        if (!review.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Review", reviewId);
        }
        if (dto.getRating()  != null) review.setRating(dto.getRating());
        if (dto.getComment() != null) review.setComment(dto.getComment());

        ReviewDTO saved = ReviewDTO.from(reviewRepository.save(review));
        productRepository.refreshRating(productId);
        return saved;
    }

    @Transactional
    public void delete(Long productId, Long reviewId, String callerEmail, boolean isAdmin) {
        ProductReview review = getReviewForWrite(reviewId, callerEmail, isAdmin);

        if (!review.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Review", reviewId);
        }
        reviewRepository.delete(review);
        productRepository.refreshRating(productId);
    }

    private ProductReview getReviewForWrite(Long reviewId, String callerEmail, boolean isAdmin) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));

        if (!isAdmin) {
            User caller = userService.getUserByEmail(callerEmail);
            if (!review.getUser().getId().equals(caller.getId())) {
                throw new AccessDeniedException("You can only modify your own reviews");
            }
        }
        return review;
    }
}
