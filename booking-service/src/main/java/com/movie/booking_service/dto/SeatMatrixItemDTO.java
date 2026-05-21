package com.movie.booking_service.dto;

import lombok.Data;

@Data
public class SeatMatrixItemDTO {
    private String id;
    private String row;
    private String col;
    private String type;
    private String status;

    private int gridRow;
    private int gridColumn;
}
