package com.movie.catalog_service.dto.response;

import lombok.Data;

@Data
public class RoomResponseDTO {
    private String id;

    private String cinemaId;
    private String cinemaName;

    private String name;
    private Integer rowCount;
    private Integer columnCount;
    private Integer totalSeats;
    private Boolean isActive;
}
