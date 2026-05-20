package com.movie.booking_service.dto;

import lombok.Data;

@Data
public class SeatStatusResponseDTO {
    private String id;
    private String rowName;

    // 1. Cập nhật kiểu String
    private String seatLabel;

    private int gridRow;
    private int gridColumn;

    // 2. Thêm seatType
    private String seatType; // "SINGLE", "DOUBLE"

    private String status;
}
