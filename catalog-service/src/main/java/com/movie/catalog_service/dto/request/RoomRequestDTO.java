package com.movie.catalog_service.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class RoomRequestDTO {
    @NotBlank(message = "ID của cụm rạp không được để trống")
    private String cinemaId;

    @NotBlank(message = "Tên phòng không được để trống")
    @JsonProperty("roomName")
    private String name;

    @NotNull(message = "Số hàng của khung lưới không được để trống")
    @Min(1) @Max(26)
    private Integer totalRows; // Đại diện cho kích thước chiều dọc lớn nhất của phòng

    @NotNull(message = "Số cột của khung lưới không được để trống")
    @Min(1) @Max(50)
    private Integer totalColumns; // Đại diện cho kích thước chiều ngang lớn nhất của phòng

    @NotEmpty(message = "Danh sách cấu hình ghế không được để trống")
    @Valid
    private List<SeatCreateRequest> seats;

    // Class nội bộ đại diện cho từng cái ghế gửi lên
    @Data
    public static class SeatCreateRequest {
        private Integer rowIndex;
        private Integer columnIndex;
        private String rowLabel;
        private String columnLabel;
        private String seatType;
    }
}