package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.SnackCombo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SnackComboRepository extends JpaRepository<SnackCombo, String> {
    @Query("SELECT c FROM SnackCombo c WHERE (:isActive IS NULL OR c.isActive = :isActive)")
    Page<SnackCombo> filterCombos(@Param("isActive") Boolean isActive, Pageable pageable);
    Optional<SnackCombo> findByName(String name);
    boolean existsByName(String name);
}
