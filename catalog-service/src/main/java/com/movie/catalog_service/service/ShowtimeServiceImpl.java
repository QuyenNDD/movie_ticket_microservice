package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.ShowtimeRequestDTO;
import com.movie.catalog_service.dto.response.ShowtimeResponseDTO;
import com.movie.catalog_service.entity.Movie;
import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.Showtime;
import com.movie.catalog_service.entity.ShowtimeStatus;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.MovieRepository;
import com.movie.catalog_service.repository.RoomRepository;
import com.movie.catalog_service.repository.ShowtimeRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShowtimeServiceImpl implements ShowtimeService{
    @Autowired
    ShowtimeRepository showtimeRepository;
    @Autowired
    MovieRepository movieRepository;
    @Autowired
    RoomRepository roomRepository;
    @Autowired
    ModelMapper modelMapper;

    private static final int CLEANING_TIME_MINUTES = 15;

    @Override
    @Transactional
    public ShowtimeResponseDTO createShowtime(ShowtimeRequestDTO request) {
        // 1. Kiểm tra Movie và Room có tồn tại không
        Movie movie = movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie", "id", request.getMovieId()));

        if (!"ACTIVE".equalsIgnoreCase(movie.getStatus())) {
            throw new APIException("Không thể tạo lịch chiếu cho phim đã dừng chiếu hoặc chưa hoạt động!");
        }

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", request.getRoomId()));

        if (!room.getIsActive()) {
            throw new APIException("Phòng chiếu này hiện đang bảo trì, không thể tạo lịch chiếu!");
        }

        if (!Boolean.TRUE.equals(room.getCinema().getIsActive())) {
            throw new APIException("Rạp chiếu này đã dừng hoạt động, không thể tạo lịch chiếu!");
        }

        Integer movieDuration = movie.getDuration();
        LocalDateTime newStartTime = request.getStartTime();
        if (!newStartTime.isAfter(LocalDateTime.now())) {
            throw new APIException("Thời gian bắt đầu suất chiếu phải nằm trong tương lai!");
        }
        LocalDateTime newEndTime = newStartTime.plusMinutes(movieDuration + CLEANING_TIME_MINUTES);

        boolean isOverlap = showtimeRepository.existsOverlappingShowtime(
                room.getId(),
                newStartTime,
                newEndTime,
                ShowtimeStatus.SCHEDULED
        );

        if (isOverlap) {
            throw new APIException("Khung giờ từ " + newStartTime + " đến " + newEndTime +
                    " đã bị trùng với một suất chiếu khác trong phòng này!");
        }

        // 4. Lưu vào Database
        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setRoom(room);
        showtime.setStartTime(newStartTime);
        showtime.setEndTime(newEndTime);
        showtime.setBasePrice(request.getBasePrice());

        Showtime savedShowtime = showtimeRepository.save(showtime);

        ShowtimeResponseDTO dto = modelMapper.map(savedShowtime, ShowtimeResponseDTO.class);
        dto.setMovieTitle(savedShowtime.getMovie().getTitle());
        dto.setMoviePoster(savedShowtime.getMovie().getPoseUrl());
        dto.setMovieDuration(savedShowtime.getMovie().getDuration());

        dto.setCinemaId(savedShowtime.getRoom().getCinema().getId());
        dto.setCinemaName(savedShowtime.getRoom().getCinema().getName());
        dto.setRoomName(savedShowtime.getRoom().getName());
        return dto;
    }

    // Lấy theo Rạp
    @Override
    public List<ShowtimeResponseDTO> getShowtimesByCinemaAndDate(String cinemaId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Showtime> showtimes = showtimeRepository.findShowtimesByCinemaAndDate(
                cinemaId,
                startOfDay,
                endOfDay,
                ShowtimeStatus.SCHEDULED
        );
        return showtimes.stream()
                .map(this::mapToResponseDTO) // Tách phần map DTO ra 1 hàm private cho gọn
                .collect(Collectors.toList());
    }

    @Override
    public List<ShowtimeResponseDTO> getShowtimesByMovieAndDate(String movieId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Showtime> showtimes = showtimeRepository.findShowtimesByMovieAndDate(
                movieId,
                startOfDay,
                endOfDay,
                ShowtimeStatus.SCHEDULED
        );
        return showtimes.stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    // Hàm private dùng chung để map DTO tránh lặp code
    private ShowtimeResponseDTO mapToResponseDTO(Showtime showtime) {
        ShowtimeResponseDTO dto = modelMapper.map(showtime, ShowtimeResponseDTO.class);
        dto.setMovieTitle(showtime.getMovie().getTitle());
        dto.setMoviePoster(showtime.getMovie().getPoseUrl());
        dto.setMovieDuration(showtime.getMovie().getDuration());
        dto.setCinemaId(showtime.getRoom().getCinema().getId());
        dto.setCinemaName(showtime.getRoom().getCinema().getName());
        dto.setRoomName(showtime.getRoom().getName());
        return dto;
    }

    @Override
    @Transactional
    public ShowtimeResponseDTO cancelShowtime(String showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime", "id", showtimeId));

        showtime.setStatus(ShowtimeStatus.CANCELLED);
        Showtime updatedShowtime = showtimeRepository.save(showtime);

        return mapToResponseDTO(updatedShowtime);
    }

    @Override
    public ShowtimeResponseDTO getShowtimeById(String showtimeId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime", "id", showtimeId));
        return mapToResponseDTO(showtime);
    }
}
