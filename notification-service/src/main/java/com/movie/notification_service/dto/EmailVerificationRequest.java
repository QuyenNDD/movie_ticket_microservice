package com.movie.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailVerificationRequest {
    @NotBlank(message = "toEmail không được để trống")
    private String toEmail;

    @NotBlank(message = "verifyLink không được để trống")
    private String verifyLink;
}
