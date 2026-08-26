package com.movie.catalog_service.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewResponseDTO {
    private String id;
    private String movieId;
    private String userId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
