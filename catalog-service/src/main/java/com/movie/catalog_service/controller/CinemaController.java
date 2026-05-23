package com.movie.catalog_service.controller;


import com.movie.catalog_service.config.AppConstants;
import com.movie.catalog_service.dto.request.CinemaRequestDTO;
import com.movie.catalog_service.dto.response.CinemaResponse;
import com.movie.catalog_service.dto.response.CinemaResponseDTO;
import com.movie.catalog_service.service.CinemaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/cinemas")
public class CinemaController {

    @Autowired
    private CinemaService cinemaService;

    // 1. Thêm rạp mới
    @PostMapping
    public ResponseEntity<CinemaResponseDTO> createCinema(@Valid @RequestBody CinemaRequestDTO request) {
        return new ResponseEntity<>(cinemaService.createCinema(request), HttpStatus.CREATED);
    }

    // 2. Cập nhật rạp
    @PutMapping("/{cinemaId}")
    public ResponseEntity<CinemaResponseDTO> updateCinema(
            @PathVariable String cinemaId,
            @Valid @RequestBody CinemaRequestDTO request) {
        return new ResponseEntity<>(cinemaService.updateCinema(cinemaId, request), HttpStatus.OK);
    }

    // 3. Lấy chi tiết 1 rạp
    @GetMapping("/{cinemaId}")
    public ResponseEntity<CinemaResponseDTO> getCinemaById(@PathVariable String cinemaId) {
        return new ResponseEntity<>(cinemaService.getCinemaById(cinemaId), HttpStatus.OK);
    }

    // 4. Xóa rạp
    @DeleteMapping("/{cinemaId}")
    public ResponseEntity<CinemaResponseDTO> deleteCinema(@PathVariable String cinemaId) {
        return new ResponseEntity<>(cinemaService.deleteCinema(cinemaId), HttpStatus.OK);
    }

    @PatchMapping("/{cinemaId}")
    public ResponseEntity<CinemaResponseDTO> reopenCinema(@PathVariable String cinemaId) {
        return new ResponseEntity<>(cinemaService.reopenCinema(cinemaId), HttpStatus.OK);
    }

    // 5. Lấy danh sách rạp (Có phân trang)
    @GetMapping
    public ResponseEntity<CinemaResponse> getAllCinemas(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_CINEMA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {

        CinemaResponse response = cinemaService.getAllCinemas(isActive, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 6.API TÌM KIẾM THEO TÊN VÀ THÀNH PHỐ
    @GetMapping("/search")
    public ResponseEntity<CinemaResponse> searchCinemas(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_CINEMA_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {

        CinemaResponse response = cinemaService.searchCinemas(name, city, isActive, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
