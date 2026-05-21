package com.movie.catalog_service.service;

import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.Seat;
import com.movie.catalog_service.entity.Showtime;
import com.movie.catalog_service.entity.Snack;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.SeatRepository;
import com.movie.catalog_service.repository.ShowtimeRepository;
import com.movie.catalog_service.repository.SnackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogServiceImpl implements CatalogService{
    @Autowired
    ShowtimeRepository showtimeRepository;

    @Autowired
    SeatRepository seatRepository;

    @Autowired
    SnackRepository snackRepository;


    @Override
    public Double getSeatPrice(String showtimeId, String seatId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime", "id", showtimeId));
        Seat existingSeat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", "id", seatId));
        Room room = showtime.getRoom();
        List<Seat> seat = room.getSeats();
        if (!seat.contains(existingSeat)) {
            throw new APIException("Seat không tồn tại trong showtime này!");
        }
        if (existingSeat.getSeatType().equals("NORMAL")) {
            return showtime.getBasePrice();
        } else if (existingSeat.getSeatType().equals("VIP")) {
            return showtime.getBasePrice() + 10000.0;
        } else if (existingSeat.getSeatType().equals("COUPLE")) {
            return showtime.getBasePrice() * 2;
        }
        return 0.0;
    }

    @Override
    public Double getSnackPrice(String snackId) {
        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new ResourceNotFoundException("Snack", "id", snackId));

        if (!snack.getIsActive()) {
            throw new APIException("Snack đã dừng bán!");
        }
        return snack.getPrice();
    }
}
