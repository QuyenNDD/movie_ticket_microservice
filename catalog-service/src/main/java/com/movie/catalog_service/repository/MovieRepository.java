package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Movie;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {
    Optional<Movie> findByTitle(String title);
    boolean existsByTitle(@NotBlank(message = "Name of movie not null") String title);
    Page<Movie> findByStatus(String status, Pageable pageable);
    Page<Movie> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}
