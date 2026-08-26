package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FavoriteRequestDTO {
    @NotBlank(message = "movieId không được để trống")
    private String movieId;
}
