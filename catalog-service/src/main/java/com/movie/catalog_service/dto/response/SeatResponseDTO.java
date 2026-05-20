package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class SeatResponseDTO {
    private String id;
    private String rowName;

    // 1. Đổi từ int seatNumber -> String seatLabel
    private String seatLabel;
    private int gridRow;
    private int gridColumn;
    // 2. Thêm trường này để Booking Service nhận diện được loại ghế
    private String seatType;
}
