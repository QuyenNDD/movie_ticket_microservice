package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class SeatResponseDTO {
    private String id;
    private String rowName;
    private Integer seatNumber;
    private String seatType;
    private String status;
}
