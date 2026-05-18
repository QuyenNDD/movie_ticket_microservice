package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.request.ShowtimeRequestDTO;
import com.movie.catalog_service.dto.response.ShowtimeResponseDTO;
import com.movie.catalog_service.service.ShowtimeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/showtimes")
public class ShowtimeController {

    @Autowired
    ShowtimeService showtimeService;

    // 1. TẠO SUẤT CHIẾU (Dành cho Admin)
    @PostMapping
    public ResponseEntity<ShowtimeResponseDTO> createShowtime(@Valid @RequestBody ShowtimeRequestDTO request) {
        return new ResponseEntity<>(showtimeService.createShowtime(request), HttpStatus.CREATED);
    }

    // 2. HỦY SUẤT CHIẾU (Dành cho Admin)
    // Dùng PATCH vì chúng ta chỉ cập nhật 1 phần dữ liệu (đổi status thành CANCELLED)
    @PatchMapping("/{showtimeId}/cancel")
    public ResponseEntity<ShowtimeResponseDTO> cancelShowtime(@PathVariable String showtimeId) {
        return new ResponseEntity<>(showtimeService.cancelShowtime(showtimeId), HttpStatus.OK);
    }

    // 3. LẤY CHI TIẾT 1 SUẤT CHIẾU (Dành cho User/Admin khi bấm chọn ghế)
    @GetMapping("/{showtimeId}")
    public ResponseEntity<ShowtimeResponseDTO> getShowtimeById(@PathVariable String showtimeId) {
        return new ResponseEntity<>(showtimeService.getShowtimeById(showtimeId), HttpStatus.OK);
    }

    // 4. LẤY LỊCH CHIẾU CỦA 1 RẠP THEO NGÀY (vd: /cinema/id-rap-cgv?date=2026-05-16)
    @GetMapping("/cinema/{cinemaId}")
    public ResponseEntity<List<ShowtimeResponseDTO>> getShowtimesByCinemaAndDate(
            @PathVariable String cinemaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // Chuyển LocalDate (2026-05-16) thành LocalDateTime (2026-05-16T00:00:00) để truyền xuống Service
        LocalDateTime startOfDay = date.atStartOfDay();
        return new ResponseEntity<>(showtimeService.getShowtimesByCinemaAndDate(cinemaId, startOfDay), HttpStatus.OK);
    }

    // 5. LẤY LỊCH CHIẾU CỦA 1 PHIM THEO NGÀY (vd: /movie/id-phim-avenger?date=2026-05-16)
    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<ShowtimeResponseDTO>> getShowtimesByMovieAndDate(
            @PathVariable String movieId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDateTime startOfDay = date.atStartOfDay();
        return new ResponseEntity<>(showtimeService.getShowtimesByMovieAndDate(movieId, startOfDay), HttpStatus.OK);
    }
}
