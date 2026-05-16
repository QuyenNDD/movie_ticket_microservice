package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Cinema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, String> {

    boolean existsByNameAndCity(String name, String city);

    @Query("SELECT c FROM Cinema c WHERE " +
            "(:name IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
            "(:city IS NULL OR LOWER(c.city) LIKE LOWER(CONCAT('%', :city, '%'))) AND " +
            "(:isActive IS NULL OR c.isActive = :isActive)")
    Page<Cinema> searchCinemas(@Param("name") String name,
                               @Param("city") String city,
                               @Param("isActive") Boolean isActive,
                               Pageable pageable);
}
