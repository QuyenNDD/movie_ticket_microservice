package com.movie.catalog_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieRequestDTO {
    private String title;
    private String description;
    private Integer duration;
    private LocalDate releaseDate;
    private String poseUrl;
    private String trailerUrl;
    private String status;
}
