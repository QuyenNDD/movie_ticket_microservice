package com.movie.catalog_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieResponseDTO {
    private String id;
    private String title;
    private Integer duration;
    private String posterUrl;
}
