package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.MovieResponseDTO;
import com.movie.catalog_service.dto.MovieRequestDTO;

public interface MovieService {
    MovieResponseDTO createMovie(MovieRequestDTO movieRequestDTO);
}
