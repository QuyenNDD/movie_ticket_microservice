package com.movie.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingPaidEmailEvent {

    private String toEmail;
    private String bookingId;
    private Long amount;
    private LocalDateTime paidAt;

    private List<SeatItem> seats;
    private List<SnackItem> snacks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatItem {
        private String seatId;
        private String seatName;
        private Long price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnackItem {
        private String snackId;
        private String snackName;
        private Integer quantity;
        private Long price;
    }
}