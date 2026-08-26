package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, String> {
    List<Review> findByMovieIdOrderByCreatedAtDesc(String movieId);
    boolean existsByMovieIdAndUserId(String movieId, String userId);
    Optional<Review> findByMovieIdAndUserId(String movieId, String userId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.movieId = :movieId")
    Double findAverageRatingByMovieId(@Param("movieId") String movieId);

    long countByMovieId(String movieId);
}
