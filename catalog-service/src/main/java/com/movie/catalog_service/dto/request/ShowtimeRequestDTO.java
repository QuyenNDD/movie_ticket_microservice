package com.movie.catalog_service.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShowtimeRequestDTO {
    @NotBlank(message = "ID phim không được để trống")
    private String movieId;

    @NotBlank(message = "ID phòng chiếu không được để trống")
    private String roomId;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    @Future(message = "Thời gian chiếu phải nằm trong tương lai")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "Giá vé cơ bản không được để trống")
    @Min(value = 0, message = "Giá vé không được âm")
    private Double basePrice;
}
