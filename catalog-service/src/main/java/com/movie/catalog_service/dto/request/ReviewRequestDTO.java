package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewRequestDTO {
    @NotBlank(message = "movieId không được để trống")
    private String movieId;

    @NotNull(message = "rating không được để trống")
    @Min(value = 1, message = "rating phải từ 1 đến 5")
    @Max(value = 5, message = "rating phải từ 1 đến 5")
    private Integer rating;

    @Size(max = 2000, message = "comment tối đa 2000 ký tự")
    private String comment;
}
