package com.movie.booking_service.service;

import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.dto.BookingResponseDTO;
import com.movie.booking_service.dto.BookingSummaryDTO;
import com.movie.booking_service.dto.RoomSeatMatrixResponseDTO;
import com.movie.booking_service.dto.SeatStatusResponseDTO;
import com.movie.booking_service.dto.TicketResponseDTO;

import java.util.List;

public interface BookingService {
    BookingResponseDTO holdSeats(String userId, BookingRequestDTO request);
    RoomSeatMatrixResponseDTO getSeatsForShowtime(String showtimeId);
    BookingResponseDTO confirmPayment(String userId, String bookingId);
    BookingResponseDTO cancelBooking(String userId, String bookingId, String reason);
    BookingResponseDTO getBookingDetails(String userId, String bookingId);
    List<BookingSummaryDTO> getMyBookings(String userId);
    List<TicketResponseDTO> getTickets(String userId, String bookingId);
    Double getSnackPriceFromCatalog(String snackId);
    Double getSeatPriceFromCatalog(String showtimeId, String seatId);
    boolean hasActiveBookingForShowtimes(List<String> showtimeIds);
}
