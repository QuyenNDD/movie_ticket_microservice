package com.movie.booking_service.dto;

import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.BookingSnack;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDTO {
    private String showtimeId;
    private LocalDateTime bookingTime;
    private Double totalPrice;
    private List<BookingSeat> bookingSeats;
    private List<BookingSnack> bookingSnacks;
}
