package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    boolean existsByUserIdAndMovieId(String userId, String movieId);
    Optional<Favorite> findByUserIdAndMovieId(String userId, String movieId);
    List<Favorite> findByUserIdOrderByCreatedAtDesc(String userId);
}
