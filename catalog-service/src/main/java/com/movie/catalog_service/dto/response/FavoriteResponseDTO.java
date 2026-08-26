package com.movie.catalog_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponseDTO {
    private String movieId;
    private String movieTitle;
    private String moviePosterUrl;
    private LocalDateTime createdAt;
}
