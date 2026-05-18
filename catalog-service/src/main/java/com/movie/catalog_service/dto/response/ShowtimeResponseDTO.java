package com.movie.catalog_service.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShowtimeResponseDTO {
    private String id;

    private String movieId;
    private String movieTitle;
    private String moviePoster;
    private Integer movieDuration;

    private String cinemaId;
    private String cinemaName;
    private String roomId;
    private String roomName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    private Double basePrice;

    private String status;
}
