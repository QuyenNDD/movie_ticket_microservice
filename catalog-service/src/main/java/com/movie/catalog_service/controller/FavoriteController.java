package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.request.FavoriteRequestDTO;
import com.movie.catalog_service.dto.response.FavoriteResponseDTO;
import com.movie.catalog_service.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/favorites")
public class FavoriteController {

    @Autowired
    FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<FavoriteResponseDTO> addFavorite(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody FavoriteRequestDTO request
    ) {
        FavoriteResponseDTO response = favoriteService.addFavorite(userId, request.getMovieId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponseDTO>> getMyFavorites(
            @RequestHeader("X-User-Id") String userId
    ) {
        List<FavoriteResponseDTO> response = favoriteService.getFavoritesByUser(userId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/{movieId}")
    public ResponseEntity<Void> removeFavorite(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String movieId
    ) {
        favoriteService.removeFavorite(userId, movieId);
        return ResponseEntity.noContent().build();
    }
}
