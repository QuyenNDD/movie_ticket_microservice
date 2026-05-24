package com.movie.notification_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BookingPaidEmailRequest {

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email người nhận không được để trống")
    private String toEmail;

    @NotBlank(message = "bookingId không được để trống")
    private String bookingId;

    @NotNull(message = "amount không được để trống")
    private Long amount;

    private String paidAt;

    @Valid
    private List<SeatItem> seats;

    @Valid
    private List<SnackItem> snacks;

    @Data
    public static class SeatItem {
        private String seatId;
        private String seatName;
        private Long price;
    }

    @Data
    public static class SnackItem {
        private String snackId;
        private String snackName;
        private Integer quantity;
        private Long price;
    }
}