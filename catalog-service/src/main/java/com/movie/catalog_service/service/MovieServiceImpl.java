package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.MovieResponseDTO;
import com.movie.catalog_service.dto.MovieRequestDTO;
import com.movie.catalog_service.entity.Movie;
import com.movie.catalog_service.repository.MovieRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MovieServiceImpl implements MovieService{
    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public MovieResponseDTO createMovie(MovieRequestDTO movieRequestDTO) {
        Movie movie = modelMapper.map(movieRequestDTO, Movie.class);
        Movie savedMovie = movieRepository.save(movie);
        return modelMapper.map(savedMovie, MovieResponseDTO.class);
    }
}
