package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SeatTypeUpdateRequestDTO {
    @NotEmpty(message = "Danh sách ID ghế không được để trống")
    private List<String> seatIds;

    @NotBlank(message = "Loại ghế không được để trống")
    private String seatType;
}
