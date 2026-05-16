package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CinemaRequestDTO {
    @NotBlank(message = "Tên rạp không được để trống")
    private String name;

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    @NotBlank(message = "Thành phố không được để trống")
    private String city;
}
