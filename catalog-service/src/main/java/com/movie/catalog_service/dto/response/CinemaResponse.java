package com.movie.catalog_service.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class CinemaResponse {
    private List<CinemaResponseDTO> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private boolean isLastPage;
}
