package com.movie.catalog_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoomRequestDTO {
    @NotBlank(message = "ID của cụm rạp không được để trống")
    private String cinemaId; // Phải biết phòng này nhét vào rạp nào

    @NotBlank(message = "Tên phòng không được để trống")
    private String name; // Ví dụ: "Room 01", "IMAX 3D"

    @NotNull(message = "Số hàng ghế không được để trống")
    @Min(value = 1, message = "Số hàng tối thiểu là 1")
    @Max(value = 26, message = "Số hàng tối đa là 26 (Từ A đến Z)")
    private Integer rowCount;

    @NotNull(message = "Số lượng ghế mỗi hàng không được để trống")
    @Min(value = 1, message = "Mỗi hàng phải có ít nhất 1 ghế")
    @Max(value = 50, message = "Mỗi hàng tối đa 50 ghế để đảm bảo hiển thị UI")
    private Integer columnCount;
}
