package com.movie.payment_service.dto;

import lombok.Data;

@Data
public class PaymentRequestDTO {
    private String bookingId;
    private String amount;
}
