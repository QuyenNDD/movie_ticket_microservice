package com.movie.booking_service.service;

import com.movie.booking_service.client.CatalogClient;
import com.movie.booking_service.config.ModelMapperConfig;
import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.dto.BookingResponseDTO;
import com.movie.booking_service.entity.Booking;
import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.BookingSnack;
import com.movie.booking_service.repository.BookingRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookingServiceImpl implements BookingService{
    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    CatalogClient catalogClient;

    @Autowired
    ModelMapper modelMapper;

    @Override
    @Transactional
    public BookingResponseDTO createBooking(String userId, BookingRequestDTO bookingRequestDTO) {
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowtimeId(bookingRequestDTO.getShowtimeId());

        double seatTotal = 0.0;
        double snackTotal = 0.0;
        if (bookingRequestDTO.getSeatIds() != null && !bookingRequestDTO.getSeatIds().isEmpty()) {
            List<BookingSeat> seats = bookingRequestDTO.getSeatIds().stream()
                    .map(seatId -> {
                        BookingSeat seat = new BookingSeat();
                        seat.setSeatId(seatId);
                        seat.setPriceAtPurchase(catalogClient.getSeatPrice(seatId));
                        seat.setBooking(booking);
                        return seat;
                    }).toList();
            booking.getBookingSeats().addAll(seats);
            seatTotal = seats.stream()
                    .mapToDouble(BookingSeat::getPriceAtPurchase)
                    .sum();
        }

        if (bookingRequestDTO.getSnacks() != null && !bookingRequestDTO.getSnacks().isEmpty()) {
            List<BookingSnack> snacks = bookingRequestDTO.getSnacks().entrySet().stream()
                    .map(snack -> {
                        BookingSnack bookingSnack = new BookingSnack();
                        bookingSnack.setSnackId(snack.getKey());
                        bookingSnack.setQuantity(snack.getValue());
                        bookingSnack.setBooking(booking);
                        bookingSnack.setPriceAtPurchase(catalogClient.getSnackPrice(snack.getKey()));
                        return bookingSnack;
                    }).toList();
            booking.getBookingSnacks().addAll(snacks);
            snackTotal = snacks.stream()
                    .mapToDouble(snack -> snack.getPriceAtPurchase() * snack.getQuantity())
                    .sum();
        }

        booking.setTotalPrice(seatTotal + snackTotal);
        Booking savedBooking = bookingRepository.save(booking);
        return modelMapper.map(savedBooking, BookingResponseDTO.class);
    }
}
