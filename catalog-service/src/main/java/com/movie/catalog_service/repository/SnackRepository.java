package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Snack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnackRepository extends JpaRepository<Snack, String> {
    @Query("SELECT s FROM Snack s WHERE (:isActive IS NULL OR s.isActive = :isActive)")
    Page<Snack> filterSnacks(@Param("isActive") Boolean isActive, Pageable pageable);
    Optional<Snack> findByTitle(String title);
    boolean existsByName(String name);
    Page<Snack> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
