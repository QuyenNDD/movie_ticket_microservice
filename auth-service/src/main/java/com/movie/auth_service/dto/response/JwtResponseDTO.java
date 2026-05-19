package com.movie.auth_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtResponseDTO {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String userName;
    private String role;
}
