package com.movie.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateNotificationRequestDTO {
    @NotBlank(message = "userId không được để trống")
    private String userId;

    @NotBlank(message = "title không được để trống")
    private String title;

    @NotBlank(message = "content không được để trống")
    private String content;

    @NotBlank(message = "type không được để trống")
    private String type;
}
