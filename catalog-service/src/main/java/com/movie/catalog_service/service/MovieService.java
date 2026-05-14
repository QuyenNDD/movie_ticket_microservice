package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.response.MovieResponse;
import com.movie.catalog_service.dto.response.MovieResponseDTO;
import com.movie.catalog_service.dto.request.MovieRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MovieService {
    MovieResponseDTO createMovie(MovieRequestDTO movieRequestDTO);
    MovieResponseDTO getMovieById(String movieId);
    MovieResponseDTO updateMovie(String movieId, MovieRequestDTO request);
    MovieResponseDTO deleteMovie(String movieId);
    MovieResponse getAllMovies(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    MovieResponseDTO updatePoster(String movieId, String newPosterUrl);
    MovieResponseDTO updateTrailer(String movieId, String newTrailerUrl);

    MovieResponse getMovieByStatus(String status, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    MovieResponse getMovieByTitle(String title, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
