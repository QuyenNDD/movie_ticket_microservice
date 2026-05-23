package com.movie.catalog_service.service;

import com.movie.catalog_service.entity.*;
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

        if (showtime.getStatus() != ShowtimeStatus.SCHEDULED) {
            throw new APIException("Suất chiếu này đã bị hủy hoặc không còn mở bán!");
        }

        if (!"ACTIVE".equalsIgnoreCase(showtime.getMovie().getStatus())) {
            throw new APIException("Phim này đã dừng chiếu, không thể đặt vé!");
        }

        Room room = showtime.getRoom();

        if (!Boolean.TRUE.equals(room.getIsActive())) {
            throw new APIException("Phòng chiếu này đã dừng hoạt động, không thể đặt vé!");
        }

        if (!Boolean.TRUE.equals(room.getCinema().getIsActive())) {
            throw new APIException("Rạp chiếu này đã dừng hoạt động, không thể đặt vé!");
        }

        // Vá nhóm 9:
        // Không dùng room.getSeats().contains(existingSeat) nữa.
        // Check trực tiếp roomId để chắc chắn ghế thuộc đúng phòng của suất chiếu.
        if (existingSeat.getRoom() == null ||
                existingSeat.getRoom().getId() == null ||
                !existingSeat.getRoom().getId().equals(room.getId())) {
            throw new APIException("Ghế không thuộc phòng chiếu của suất chiếu này!");
        }

        if ("MAINTENANCE".equalsIgnoreCase(existingSeat.getSeatType())) {
            throw new APIException("Ghế này đang bảo trì, không thể đặt vé!");
        }

        if ("NORMAL".equalsIgnoreCase(existingSeat.getSeatType())) {
            return showtime.getBasePrice();
        }

        if ("VIP".equalsIgnoreCase(existingSeat.getSeatType())) {
            return showtime.getBasePrice() + 10000.0;
        }

        // Rule rõ cho COUPLE:
        // 1 ghế COUPLE là 1 seatId vật lý nhưng tính tiền như 2 ghế.
        if ("COUPLE".equalsIgnoreCase(existingSeat.getSeatType())) {
            return showtime.getBasePrice() * 2;
        }

        throw new APIException("Loại ghế không hợp lệ!");
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
