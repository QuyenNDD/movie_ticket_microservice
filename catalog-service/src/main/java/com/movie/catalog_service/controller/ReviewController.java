package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.request.ReviewRequestDTO;
import com.movie.catalog_service.dto.request.ReviewUpdateRequestDTO;
import com.movie.catalog_service.dto.response.ReviewResponseDTO;
import com.movie.catalog_service.dto.response.ReviewSummaryResponseDTO;
import com.movie.catalog_service.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/reviews")
public class ReviewController {

    @Autowired
    ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ReviewResponseDTO> createReview(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody ReviewRequestDTO request
    ) {
        ReviewResponseDTO response = reviewService.createReview(userId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<ReviewResponseDTO>> getReviewsByMovie(@PathVariable String movieId) {
        List<ReviewResponseDTO> response = reviewService.getReviewsByMovie(movieId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/movie/{movieId}/summary")
    public ResponseEntity<ReviewSummaryResponseDTO> getReviewSummary(@PathVariable String movieId) {
        ReviewSummaryResponseDTO response = reviewService.getReviewSummary(movieId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponseDTO> updateReview(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String reviewId,
            @Valid @RequestBody ReviewUpdateRequestDTO request
    ) {
        ReviewResponseDTO response = reviewService.updateReview(userId, reviewId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String reviewId
    ) {
        reviewService.deleteReview(userId, reviewId);
        return ResponseEntity.noContent().build();
    }
}
