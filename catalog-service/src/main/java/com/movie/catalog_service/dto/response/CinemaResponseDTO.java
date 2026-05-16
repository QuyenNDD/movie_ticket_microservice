package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class CinemaResponseDTO {
    private String id;
    private String name;
    private String address;
    private String city;
    private Boolean isActive;
}
