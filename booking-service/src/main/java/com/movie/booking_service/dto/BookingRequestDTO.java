package com.movie.booking_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDTO {
    private String showtimeId;
    private List<String> seatIds;
    private Map<String, Integer> snacks;
}
