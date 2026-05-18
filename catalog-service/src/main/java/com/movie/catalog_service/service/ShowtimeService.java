package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.ShowtimeRequestDTO;
import com.movie.catalog_service.dto.response.ShowtimeResponseDTO;
import com.movie.catalog_service.entity.Showtime;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeService {
    ShowtimeResponseDTO createShowtime(ShowtimeRequestDTO request);
    List<ShowtimeResponseDTO> getShowtimesByCinemaAndDate(String cinemaId, LocalDateTime date);
    List<ShowtimeResponseDTO> getShowtimesByMovieAndDate(String movieId, LocalDateTime date);
    ShowtimeResponseDTO cancelShowtime(String showtimeId);
    ShowtimeResponseDTO getShowtimeById(String showtimeId);
}
