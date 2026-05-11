package com.movie.booking_service.controller;

import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.dto.BookingResponseDTO;
import com.movie.booking_service.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/booking")
public class BookingController {
    @Autowired
    BookingService bookingService;

    @PostMapping()
    public ResponseEntity<BookingResponseDTO> createBooking(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody BookingRequestDTO requestDTO
            ){
        BookingResponseDTO responseDTO = bookingService.createBooking(userId, requestDTO);
        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }
}
