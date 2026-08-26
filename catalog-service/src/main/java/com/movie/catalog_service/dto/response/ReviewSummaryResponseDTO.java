package com.movie.catalog_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryResponseDTO {
    private String movieId;
    private double averageRating;
    private long reviewCount;
}
