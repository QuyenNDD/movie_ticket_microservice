package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.ReviewRequestDTO;
import com.movie.catalog_service.dto.request.ReviewUpdateRequestDTO;
import com.movie.catalog_service.dto.response.ReviewResponseDTO;
import com.movie.catalog_service.dto.response.ReviewSummaryResponseDTO;

import java.util.List;

public interface ReviewService {
    ReviewResponseDTO createReview(String userId, ReviewRequestDTO request);
    List<ReviewResponseDTO> getReviewsByMovie(String movieId);
    ReviewSummaryResponseDTO getReviewSummary(String movieId);
    ReviewResponseDTO updateReview(String userId, String reviewId, ReviewUpdateRequestDTO request);
    void deleteReview(String userId, String reviewId);
}
