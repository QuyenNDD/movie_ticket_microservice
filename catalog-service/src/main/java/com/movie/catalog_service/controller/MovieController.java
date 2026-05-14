package com.movie.catalog_service.controller;

import com.movie.catalog_service.config.AppConstants;
import com.movie.catalog_service.dto.request.MovieRequestDTO;
import com.movie.catalog_service.dto.response.MovieResponse;
import com.movie.catalog_service.dto.response.MovieResponseDTO;
import com.movie.catalog_service.service.MovieService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/catalog/movies")
public class MovieController {

    @Autowired
    MovieService movieService;

    @GetMapping()
    public ResponseEntity<MovieResponse> getAllMovies(
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_MOVIE_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {
        MovieResponse response = movieService.getAllMovies(pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<MovieResponse> getMoviesByStatus(
            @PathVariable String status,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_MOVIE_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {
        MovieResponse response = movieService.getMovieByStatus(status, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/search/{title}")
    public ResponseEntity<MovieResponse> getMoviesByTitle(
            @PathVariable String title,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_MOVIE_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {
        MovieResponse response = movieService.getMovieByTitle(title, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping()
    public ResponseEntity<MovieResponseDTO> createMovie(@Valid @RequestBody MovieRequestDTO movieRequestDTO){
        MovieResponseDTO savedMovie = movieService.createMovie(movieRequestDTO);
        return new ResponseEntity<>(savedMovie, HttpStatus.CREATED);
    }

    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponseDTO> getMovieById(@PathVariable String movieId) {
        MovieResponseDTO response = movieService.getMovieById(movieId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{movieId}")
    public ResponseEntity<MovieResponseDTO> updateMovie(@PathVariable String movieId,
                                                        @Valid @RequestBody MovieRequestDTO request) {
        MovieResponseDTO response = movieService.updateMovie(movieId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<MovieResponseDTO> deleteMovie(@PathVariable String movieId) {
        MovieResponseDTO response = movieService.deleteMovie(movieId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/{movieId}/poster")
    public ResponseEntity<MovieResponseDTO> updateMoviePoster(@PathVariable String movieId,
                                                              @RequestParam String poseUrl) {
        MovieResponseDTO response = movieService.updatePoster(movieId, poseUrl);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    @PatchMapping("/{movieId}/trailer")
    public ResponseEntity<MovieResponseDTO> updateMovieTrailer(@PathVariable String movieId,
                                                              @RequestParam String trailerUrl) {
        MovieResponseDTO response = movieService.updateTrailer(movieId, trailerUrl);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
