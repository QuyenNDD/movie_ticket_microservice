package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.response.FavoriteResponseDTO;

import java.util.List;

public interface FavoriteService {
    FavoriteResponseDTO addFavorite(String userId, String movieId);
    void removeFavorite(String userId, String movieId);
    List<FavoriteResponseDTO> getFavoritesByUser(String userId);
}
