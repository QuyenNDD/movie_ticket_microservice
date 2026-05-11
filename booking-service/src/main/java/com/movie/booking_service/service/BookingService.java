package com.movie.booking_service.service;

import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.dto.BookingResponseDTO;

public interface BookingService {
    BookingResponseDTO  createBooking(String userId, BookingRequestDTO bookingRequestDTO);
}
