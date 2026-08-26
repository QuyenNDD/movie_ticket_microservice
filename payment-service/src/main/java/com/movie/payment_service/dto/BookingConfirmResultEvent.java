package com.movie.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmResultEvent {
    private String paymentId;
    private String bookingId;
    private boolean success;
    private String errorMessage;
}
