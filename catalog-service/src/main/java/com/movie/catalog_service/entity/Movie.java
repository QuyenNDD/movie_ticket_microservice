package com.movie.catalog_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "movies")
public class Movie {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "pose_url", length = 500)
    private String poseUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(length = 50)
    private String status;

    @PrePersist
    public void ensureId() {
        if (this.id == null){
            this.id = UUID.randomUUID().toString();
        }
    }
}
