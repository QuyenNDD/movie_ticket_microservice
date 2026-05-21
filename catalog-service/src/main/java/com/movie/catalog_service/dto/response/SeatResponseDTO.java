package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class SeatResponseDTO {
    private String id;
    private String rowName;
    private String seatLabel;
    private int gridRow;
    private int gridColumn;
    private String seatType;
}
