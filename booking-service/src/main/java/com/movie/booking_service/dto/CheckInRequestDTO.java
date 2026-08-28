package com.movie.booking_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckInRequestDTO {
    @NotBlank(message = "Mã QR không được để trống")
    private String qrCode;
}
