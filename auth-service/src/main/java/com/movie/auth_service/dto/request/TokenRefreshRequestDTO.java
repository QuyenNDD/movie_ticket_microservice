package com.movie.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshRequestDTO {
    @NotBlank(message = "Refresh Token không được để trống")
    private String refreshToken;
}
