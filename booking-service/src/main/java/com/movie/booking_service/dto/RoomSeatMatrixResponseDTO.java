package com.movie.booking_service.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoomSeatMatrixResponseDTO {
    private String roomId;
    private String roomName;
    private int totalRows;
    private int totalColumns;
    private List<List<SeatMatrixItemDTO>> seatMatrix;
}
