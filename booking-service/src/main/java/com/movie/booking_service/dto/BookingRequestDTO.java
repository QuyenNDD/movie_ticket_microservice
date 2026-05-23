package com.movie.booking_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDTO {
    @NotBlank(message = "ID suất chiếu không được để trống")
    private String showtimeId;

    @NotEmpty(message = "Bạn phải chọn ít nhất 1 ghế để tiếp tục")
    @Valid
    private List<SeatRequest> seats;

    @Valid
    private List<SnackRequest> snacks;

    @Data
    public static class SeatRequest {

        @NotBlank(message = "ID ghế không được để trống")
        private String seatId;
    }

    @Data
    public static class SnackRequest {

        @NotBlank(message = "ID bắp nước không được để trống")
        private String snackId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        @Max(value = 20, message = "Mỗi loại snack chỉ được đặt tối đa 20 phần")
        private Integer quantity;
    }
}
