package com.movie.booking_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String cancellationReason;
    private String refundStatus;

    // Thêm để gửi mail hiển thị tên ghế
    private List<SeatItem> seats;

    // Thêm để gửi mail hiển thị tên snack
    private List<SnackItem> snacks;

    private List<ComboItem> combos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatItem {
        private String seatId;
        private String seatName; // Ví dụ: A1, A2
        private Long price;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SnackItem {
        private String snackId;
        private String snackName; // Ví dụ: Bắp rang bơ, Coca
        private Integer quantity;
        private Long price; // đơn giá lúc mua
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComboItem {
        private String comboId;
        private String comboName;
        private Integer quantity;
        private Long price; // đơn giá lúc mua
    }
}