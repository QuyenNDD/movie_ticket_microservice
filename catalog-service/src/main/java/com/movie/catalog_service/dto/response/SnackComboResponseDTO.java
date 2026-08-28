package com.movie.catalog_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SnackComboResponseDTO {
    private String id;
    private String name;
    private String description;
    private Double price;
    private String imageUrl;
    private Boolean isActive;
    private List<ComboItemResponse> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComboItemResponse {
        private String snackId;
        private String snackName;
        private Integer quantity;
    }
}
