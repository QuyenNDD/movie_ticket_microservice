package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
public class MovieRequestDTO {

    @NotBlank(message = "Name of movie not null")
    private String title;

    @NotBlank(message = "Description not null")
    private String description;

    @NotNull(message = "Duration not null")
    @Min(value = 1, message = "At least 1 minute")
    private Integer duration;

    @NotNull(message = "Release date not null")
    private LocalDate releaseDate;

    @NotBlank(message = "Pose Url not blank")
    private String poseUrl;

    @NotBlank(message = "Trailer Url not blank")
    private String trailerUrl;

    @NotBlank(message = "Status not null")
    private String status;
}
