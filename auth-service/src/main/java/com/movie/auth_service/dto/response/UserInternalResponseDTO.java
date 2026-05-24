package com.movie.auth_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInternalResponseDTO {
    private String id;
    private String userName;
    private String email;
    private String role;
}