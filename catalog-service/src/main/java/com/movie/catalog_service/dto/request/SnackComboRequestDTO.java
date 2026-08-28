package com.movie.catalog_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SnackComboRequestDTO {
    @NotBlank(message = "Name of combo is not blank")
    private String name;

    private String description;

    @NotNull(message = "Price is not null")
    @DecimalMin(value = "1000.0", message = "Giá combo phải từ 1000 trở lên")
    private Double price;

    @NotBlank(message = "Image is not blank")
    private String imageUrl;

    private Boolean isActive = true;

    @NotEmpty(message = "Combo phải có ít nhất 1 món bên trong")
    @Valid
    private List<ComboItemRequest> items;

    @Data
    public static class ComboItemRequest {
        @NotBlank(message = "ID snack không được để trống")
        private String snackId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        private Integer quantity;
    }
}
