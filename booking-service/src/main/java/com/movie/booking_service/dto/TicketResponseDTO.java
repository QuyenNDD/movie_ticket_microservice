package com.movie.booking_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketResponseDTO {
    private String ticketId;
    private String seatId;
    private String seatName;
    private String qrCode;
    private LocalDateTime checkedInAt;
    private String checkedInBy;
}
