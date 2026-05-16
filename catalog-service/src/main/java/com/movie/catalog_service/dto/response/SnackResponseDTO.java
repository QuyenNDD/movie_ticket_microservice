package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class SnackResponseDTO {
    private String id;
    private String name;
    private String description;
    private Double price;
    private String imageUrl;
    private Boolean isActive;
}
