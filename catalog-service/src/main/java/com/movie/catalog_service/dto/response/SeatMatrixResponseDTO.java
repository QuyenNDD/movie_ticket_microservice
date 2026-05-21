package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class SeatMatrixResponseDTO {
    private String id;
    private String row;  // Sẽ lấy từ rowName (VD: "A")
    private String col;  // Sẽ lấy từ seatLabel (VD: "1", "2")
    private String type;
}
