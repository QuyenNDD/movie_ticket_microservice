package com.movie.payment_service.dto;

import com.movie.payment_service.entity.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundResponseDTO {
    private String refundTransactionId;
    private String bookingId;
    private Long amount;
    private RefundStatus status;
    private String message;
}
