package com.movie.booking_service.message;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketEmailMessage implements Serializable {
    private String toEmail;
    private String bookingId;
    private String movieTitle;
    private String seatNames;
    private String cinemaName;
    private String showtimeInfo;
}
