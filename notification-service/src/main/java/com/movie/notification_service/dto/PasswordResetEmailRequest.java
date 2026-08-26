package com.movie.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetEmailRequest {
    @NotBlank(message = "toEmail không được để trống")
    private String toEmail;

    @NotBlank(message = "resetLink không được để trống")
    private String resetLink;
}
