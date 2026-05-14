package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.response.MovieResponse;
import com.movie.catalog_service.dto.response.MovieResponseDTO;
import com.movie.catalog_service.dto.request.MovieRequestDTO;
import com.movie.catalog_service.entity.Movie;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.MovieRepository;
import com.movie.catalog_service.service.file.FileUploadService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class MovieServiceImpl implements MovieService{
    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    FileUploadService fileUploadService;

    @Override
    public MovieResponseDTO createMovie(MovieRequestDTO request) {
        if (request.getReleaseDate().isBefore(LocalDate.now())){
            throw new APIException("Release day is not before now");
        }

        if (movieRepository.findByTitle(request.getTitle()).isPresent()) {
            throw new APIException("Title is available");
        }

        Movie movie = Movie.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .duration(request.getDuration())
                .releaseDate(request.getReleaseDate())
                .poseUrl(request.getPoseUrl())
                .trailerUrl(request.getTrailerUrl())
                .status(request.getStatus())
                .build();

        Movie savedMovie = movieRepository.save(movie);
        return modelMapper.map(savedMovie, MovieResponseDTO.class);
    }

    @Override
    public MovieResponse getAllMovies(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Movie> moviePage = movieRepository.findAll(pageDetails);
        List<Movie> movies = moviePage.getContent();
        List<MovieResponseDTO> movieResponseDTOS = movies.stream()
                .map(movie -> modelMapper.map(movie, MovieResponseDTO.class))
                .toList();

        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setContent(movieResponseDTOS);
        movieResponse.setPageNumber(moviePage.getNumber());
        movieResponse.setTotalPages(moviePage.getTotalPages());
        movieResponse.setPageSize(moviePage.getSize());
        movieResponse.setTotalElements(moviePage.getTotalElements());
        movieResponse.setLastPage(moviePage.isLast());
        return movieResponse;
    }

    @Override
    public MovieResponseDTO updatePoster(String movieId, String newPosterUrl) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));
        movie.setPoseUrl(newPosterUrl);
        Movie savedMovie = movieRepository.save(movie);
        return modelMapper.map(savedMovie, MovieResponseDTO.class);

    }

    @Override
    public MovieResponseDTO updateTrailer(String movieId, String newTrailerUrl) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));
        movie.setTrailerUrl(newTrailerUrl);
        Movie savedMovie = movieRepository.save(movie);
        return modelMapper.map(savedMovie, MovieResponseDTO.class);
    }

    @Override
    public MovieResponse getMovieByStatus(String status, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Page<Movie> moviePage = movieRepository.findByStatus(status, pageDetails);
        List<Movie> movies = moviePage.getContent();

        List<MovieResponseDTO> movieResponseDTOS = movies.stream()
                .map(movie -> modelMapper.map(movie, MovieResponseDTO.class))
                .toList();
        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setContent(movieResponseDTOS);
        movieResponse.setPageNumber(moviePage.getNumber());
        movieResponse.setTotalPages(moviePage.getTotalPages());
        movieResponse.setPageSize(moviePage.getSize());
        movieResponse.setTotalElements(moviePage.getTotalElements());
        movieResponse.setLastPage(moviePage.isLast());
        return movieResponse;
    }

    @Override
    public MovieResponse getMovieByTitle(String title, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);

        Page<Movie> moviePage = movieRepository.findByTitleContainingIgnoreCase(title, pageDetails);
        List<Movie> movies = moviePage.getContent();

        List<MovieResponseDTO> movieResponseDTOS = movies.stream()
                .map(movie -> modelMapper.map(movie, MovieResponseDTO.class))
                .toList();
        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setContent(movieResponseDTOS);
        movieResponse.setPageNumber(moviePage.getNumber());
        movieResponse.setTotalPages(moviePage.getTotalPages());
        movieResponse.setPageSize(moviePage.getSize());
        movieResponse.setTotalElements(moviePage.getTotalElements());
        movieResponse.setLastPage(moviePage.isLast());
        return movieResponse;
    }

    @Override
    public MovieResponseDTO getMovieById(String movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));
        return modelMapper.map(movie, MovieResponseDTO.class);
    }

    @Override
    public MovieResponseDTO updateMovie(String movieId, MovieRequestDTO request) {
        Movie existingMovie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));

        if (!Objects.equals(existingMovie.getTitle(), request.getTitle())){
            if (movieRepository.existsByTitle(request.getTitle())) {
                throw new APIException("Title " + request.getTitle() + " is available");
            }
            existingMovie.setTitle(request.getTitle());
        }
        if (request.getReleaseDate().isBefore(LocalDate.now())){
            throw new APIException("Release day is not before now");
        }
        existingMovie.setDescription(request.getDescription());
        existingMovie.setDuration(request.getDuration());
        existingMovie.setReleaseDate(request.getReleaseDate());
        existingMovie.setPoseUrl(request.getPoseUrl());
        existingMovie.setTrailerUrl(request.getTrailerUrl());
        existingMovie.setStatus(request.getStatus());

        Movie savedMovie = movieRepository.save(existingMovie);
        return modelMapper.map(savedMovie, MovieResponseDTO.class);
    }

    @Override
    public MovieResponseDTO deleteMovie(String movieId) {
        Movie existingMovie = movieRepository.findById(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", movieId));
        existingMovie.setStatus("STOPPED");
        movieRepository.save(existingMovie);
        return modelMapper.map(existingMovie, MovieResponseDTO.class);
    }

}
