package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class SnackRequestDTO {
    @NotBlank(message = "Name of snack is not blank")
    private String name;

    private String description;

    @NotNull(message = "Price is not null")
    @DecimalMin(value = "1000.0", message = "Giá snack phải từ 1000 trở lên")
    private Double price;

    @NotBlank(message = "Image is not blank")
    private String imageUrl;

    private Boolean isActive = true;
}
