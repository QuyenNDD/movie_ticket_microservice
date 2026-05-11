package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.MovieRequestDTO;
import com.movie.catalog_service.dto.MovieResponseDTO;
import com.movie.catalog_service.service.MovieService;
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
    public String getAllMovies() {
        return "This is movies";
    }

    @PostMapping()
    public ResponseEntity<MovieResponseDTO> createMovie(@RequestBody MovieRequestDTO movieRequestDTO){
        MovieResponseDTO savedMovie = movieService.createMovie(movieRequestDTO);
        return new ResponseEntity<>(savedMovie, HttpStatus.CREATED);
    }
}
