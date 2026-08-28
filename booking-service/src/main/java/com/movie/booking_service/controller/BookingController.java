package com.movie.booking_service.controller;

import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.dto.BookingResponseDTO;
import com.movie.booking_service.dto.BookingSummaryDTO;
import com.movie.booking_service.dto.CancelBookingRequestDTO;
import com.movie.booking_service.dto.RoomSeatMatrixResponseDTO;
import com.movie.booking_service.dto.SeatStatusResponseDTO;
import com.movie.booking_service.dto.TicketResponseDTO;
import com.movie.booking_service.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/booking")
public class BookingController {
    @Autowired
    BookingService bookingService;

    @GetMapping("/showtimes/{showtimeId}/seats")
    public ResponseEntity<RoomSeatMatrixResponseDTO> getSeatMap(@PathVariable String showtimeId) {
        return new ResponseEntity<>(bookingService.getSeatsForShowtime(showtimeId), HttpStatus.OK);
    }

    @PostMapping("/hold")
    public ResponseEntity<BookingResponseDTO> holdSeats(
            @RequestHeader("x-user-id") String userId,
            @Valid @RequestBody BookingRequestDTO request) {
        BookingResponseDTO response = bookingService.holdSeats(userId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingResponseDTO> confirmPayment(
            @RequestHeader("x-user-id") String userId, // Yêu cầu Header từ FE
            @PathVariable String bookingId) {

        // Truyền cả userId và bookingId xuống Service để nó kiểm tra chéo
        BookingResponseDTO response = bookingService.confirmPayment(userId, bookingId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponseDTO> cancelBooking(
            @RequestHeader("X-User-Id") String userId, // Bắt buộc phải có userId từ Gateway để bảo mật
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequestDTO request) {

        String reason = request != null ? request.getReason() : null;
        BookingResponseDTO response = bookingService.cancelBooking(userId, bookingId, reason);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponseDTO> getBookingDetails(
            @RequestHeader("X-User-Id") String userId, // Bắt buộc có userId do API Gateway tự động tiêm vào
            @PathVariable String bookingId) {

        // Gọi xuống Service để kiểm tra bảo mật chính chủ và tính số giây còn lại
        BookingResponseDTO response = bookingService.getBookingDetails(userId, bookingId);

        // Trả về JSON giàu thông tin cùng HTTP Status 200 OK
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingId}/tickets")
    public ResponseEntity<List<TicketResponseDTO>> getTickets(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String bookingId) {

        List<TicketResponseDTO> response = bookingService.getTickets(userId, bookingId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<List<BookingSummaryDTO>> getMyBookings(
            @RequestHeader("X-User-Id") String userId) {

        List<BookingSummaryDTO> response = bookingService.getMyBookings(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/internal/showtimes/has-active-bookings")
    public ResponseEntity<Boolean> hasActiveBookingsForShowtimes(@RequestBody List<String> showtimeIds) {
        boolean result = bookingService.hasActiveBookingForShowtimes(showtimeIds);
        return ResponseEntity.ok(result);
    }
}
