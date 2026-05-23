package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.CinemaRequestDTO;
import com.movie.catalog_service.dto.response.CinemaResponse;
import com.movie.catalog_service.dto.response.CinemaResponseDTO;

public interface CinemaService {
    CinemaResponseDTO createCinema(CinemaRequestDTO request);

    CinemaResponseDTO updateCinema(String cinemaId, CinemaRequestDTO request);

    CinemaResponseDTO getCinemaById(String cinemaId);

    CinemaResponseDTO deleteCinema(String cinemaId);

    CinemaResponse getAllCinemas(Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    CinemaResponse searchCinemas(String name, String city, Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    CinemaResponseDTO reopenCinema(String cinemaId);
}
