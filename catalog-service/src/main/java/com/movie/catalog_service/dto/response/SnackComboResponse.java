package com.movie.catalog_service.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class SnackComboResponse {
    private List<SnackComboResponseDTO> content;
    private Integer pageNumber;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private boolean lastPage;
}
