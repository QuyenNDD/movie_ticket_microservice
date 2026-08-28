package com.movie.booking_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSummaryDTO {
    private String bookingId;
    private String showtimeId;
    private String status;
    private Double totalPrice;
    private LocalDateTime bookingTime;
    private long expiresInSeconds;
}
