package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.response.FavoriteResponseDTO;
import com.movie.catalog_service.entity.Favorite;
import com.movie.catalog_service.entity.Movie;
import com.movie.catalog_service.exception.DuplicateResourceException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.FavoriteRepository;
import com.movie.catalog_service.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    @Autowired
    FavoriteRepository favoriteRepository;

    @Autowired
    MovieRepository movieRepository;

    @Override
    public FavoriteResponseDTO addFavorite(String userId, String movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "movieId", movieId));

        if (favoriteRepository.existsByUserIdAndMovieId(userId, movieId)) {
            throw new DuplicateResourceException("Phim này đã có trong danh sách yêu thích!");
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .movieId(movieId)
                .build();

        Favorite savedFavorite = favoriteRepository.save(favorite);
        return toResponseDTO(savedFavorite, movie);
    }

    @Override
    public void removeFavorite(String userId, String movieId) {
        Favorite favorite = favoriteRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Favorite", "movieId", movieId));

        favoriteRepository.delete(favorite);
    }

    @Override
    public List<FavoriteResponseDTO> getFavoritesByUser(String userId) {
        List<Favorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<String> movieIds = favorites.stream().map(Favorite::getMovieId).toList();
        Map<String, Movie> moviesById = movieRepository.findAllById(movieIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(Movie::getId, Function.identity()));

        return favorites.stream()
                .map(favorite -> toResponseDTO(favorite, moviesById.get(favorite.getMovieId())))
                .toList();
    }

    private FavoriteResponseDTO toResponseDTO(Favorite favorite, Movie movie) {
        return new FavoriteResponseDTO(
                favorite.getMovieId(),
                movie == null ? null : movie.getTitle(),
                movie == null ? null : movie.getPoseUrl(),
                favorite.getCreatedAt()
        );
    }
}
