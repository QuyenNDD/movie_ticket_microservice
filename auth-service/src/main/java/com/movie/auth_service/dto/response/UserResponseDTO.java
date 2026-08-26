package com.movie.auth_service.dto.response;

import lombok.Data;

@Data
public class UserResponseDTO {
    private String id;
    private String userName;
    private String email;
    private String role;
    private boolean emailVerified;
}
