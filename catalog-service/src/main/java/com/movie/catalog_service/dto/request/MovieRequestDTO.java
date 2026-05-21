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
    @NotBlank(message = "Title of movie not blank")
    private String title;

    @NotBlank(message = "Genre not blank")
    private String genre;

    @NotBlank(message = "Country not blank")
    private String country;

    @NotBlank(message = "Language not blank")
    private String language;

    @NotBlank(message = "Age restriction not blank")
    private String ageRestriction;

    @NotBlank(message = "Director not blank")
    private String director;

    @NotBlank(message = "Actors not blank")
    private String actors;

    @NotBlank(message = "Description not blank")
    private String description;

    @NotNull(message = "Duration not null")
    @Min(value = 1, message = "At least 1 minute")
    private Integer duration;

    @NotNull(message = "Release date not null")
    private LocalDate releaseDate;

    // Giữ nguyên poseUrl theo yêu cầu
    @NotBlank(message = "Pose Url not blank")
    private String poseUrl;

    @NotBlank(message = "Trailer Url not blank")
    private String trailerUrl;

    @NotBlank(message = "Status not blank")
    private String status;
}
