package com.movie.booking_service.dto;

import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.BookingSnack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponseDTO {
    private String bookingId;
    private String status;
    private String message;
    private Double totalPrice;
    private long expiresInSeconds;
}
