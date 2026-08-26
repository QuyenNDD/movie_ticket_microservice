package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.ReviewRequestDTO;
import com.movie.catalog_service.dto.request.ReviewUpdateRequestDTO;
import com.movie.catalog_service.dto.response.ReviewResponseDTO;
import com.movie.catalog_service.dto.response.ReviewSummaryResponseDTO;
import com.movie.catalog_service.entity.Review;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.DuplicateResourceException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.MovieRepository;
import com.movie.catalog_service.repository.ReviewRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public ReviewResponseDTO createReview(String userId, ReviewRequestDTO request) {
        if (!movieRepository.existsById(request.getMovieId())) {
            throw new ResourceNotFoundException("Movie", "movieId", request.getMovieId());
        }

        if (reviewRepository.existsByMovieIdAndUserId(request.getMovieId(), userId)) {
            throw new DuplicateResourceException("Bạn đã đánh giá phim này rồi. Vui lòng sửa đánh giá cũ thay vì tạo mới.");
        }

        Review review = Review.builder()
                .movieId(request.getMovieId())
                .userId(userId)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review savedReview = reviewRepository.save(review);
        return modelMapper.map(savedReview, ReviewResponseDTO.class);
    }

    @Override
    public List<ReviewResponseDTO> getReviewsByMovie(String movieId) {
        return reviewRepository.findByMovieIdOrderByCreatedAtDesc(movieId)
                .stream()
                .map(review -> modelMapper.map(review, ReviewResponseDTO.class))
                .toList();
    }

    @Override
    public ReviewSummaryResponseDTO getReviewSummary(String movieId) {
        Double averageRating = reviewRepository.findAverageRatingByMovieId(movieId);
        long reviewCount = reviewRepository.countByMovieId(movieId);
        return new ReviewSummaryResponseDTO(movieId, averageRating == null ? 0 : averageRating, reviewCount);
    }

    @Override
    public ReviewResponseDTO updateReview(String userId, String reviewId, ReviewUpdateRequestDTO request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));

        if (!review.getUserId().equals(userId)) {
            throw new APIException("Bạn không có quyền sửa đánh giá này!");
        }

        review.setRating(request.getRating());
        review.setComment(request.getComment());

        Review savedReview = reviewRepository.save(review);
        return modelMapper.map(savedReview, ReviewResponseDTO.class);
    }

    @Override
    public void deleteReview(String userId, String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "reviewId", reviewId));

        if (!review.getUserId().equals(userId)) {
            throw new APIException("Bạn không có quyền xóa đánh giá này!");
        }

        reviewRepository.delete(review);
    }
}
