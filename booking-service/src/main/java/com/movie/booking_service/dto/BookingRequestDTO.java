package com.movie.booking_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @Valid // Kích hoạt validate cho từng phần tử bên trong List
    private List<SeatRequest> seats;

    @Valid // Có thể rỗng (nếu khách không mua bắp nước), nhưng nếu có gửi thì phải validate
    private List<SnackRequest> snacks;

    // ==========================================
    // CLASS NỘI BỘ (Chứa thông tin Ghế)
    // ==========================================
    @Data
    public static class SeatRequest {
        @NotBlank(message = "ID ghế không được để trống")
        private String seatId;

        @NotNull(message = "Giá ghế không được để trống")
        @Min(value = 0, message = "Giá ghế không được âm")
        private Double price; // ModelMapper sẽ tự động map cái này thành 'priceAtPurchase' ở Entity
    }

    // ==========================================
    // CLASS NỘI BỘ (Chứa thông tin Bắp nước)
    // ==========================================
    @Data
    public static class SnackRequest {
        @NotBlank(message = "ID bắp nước không được để trống")
        private String snackId;

        @NotNull(message = "Số lượng không được để trống")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        private Integer quantity;

        @NotNull(message = "Giá bắp nước không được để trống")
        @Min(value = 0, message = "Giá bắp nước không được âm")
        private Double price; // Tương tự, sẽ được map thành 'priceAtPurchase'
    }
}
