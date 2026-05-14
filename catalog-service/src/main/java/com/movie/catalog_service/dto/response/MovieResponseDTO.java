package com.movie.catalog_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieResponseDTO {
    private String id;
    private String title;
    private String description;
    private Integer duration;
    private LocalDate releaseDate;
    private String poseUrl;
    private String trailerUrl;
    private String status;
}
