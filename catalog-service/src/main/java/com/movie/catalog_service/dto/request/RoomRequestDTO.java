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
    @Min(value = 1, message = "Số hàng phải từ 1 trở lên")
    @Max(value = 26, message = "Số hàng tối đa là 26")
    private Integer totalRows;

    @NotNull(message = "Số cột của khung lưới không được để trống")
    @Min(value = 1, message = "Số cột phải từ 1 trở lên")
    @Max(value = 50, message = "Số cột tối đa là 50")
    private Integer totalColumns;

    @NotEmpty(message = "Danh sách cấu hình ghế không được để trống")
    @Valid
    private List<SeatCreateRequest> seats;

    @Data
    public static class SeatCreateRequest {
        @NotNull(message = "rowIndex không được để trống")
        @Min(value = 0, message = "rowIndex phải từ 0 trở lên")
        private Integer rowIndex;

        @NotNull(message = "columnIndex không được để trống")
        @Min(value = 0, message = "columnIndex phải từ 0 trở lên")
        private Integer columnIndex;

        private String rowLabel;

        private String columnLabel;

        @NotBlank(message = "seatType không được để trống")
        private String seatType;
    }
}