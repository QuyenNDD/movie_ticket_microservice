package com.movie.booking_service.repository;

import com.movie.booking_service.entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, String> {
    @Query("SELECT bs.seatId FROM BookingSeat bs JOIN bs.booking b " +
            "WHERE b.showtimeId = :showtimeId AND b.status = 'PAID'")
    List<String> findPaidSeatIdsByShowtime(@Param("showtimeId") String showtimeId);

    @Query("SELECT COUNT(bs) > 0 FROM BookingSeat bs JOIN bs.booking b " +
            "WHERE b.showtimeId = :showtimeId " +
            "AND b.status = 'PAID' " +
            "AND bs.seatId IN :seatIds")
    boolean checkIfSeatsArePaid(@Param("showtimeId") String showtimeId, @Param("seatIds") List<String> seatIds);

    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.bookingTime <= :timeLimit")
    List<Booking> findExpiredBookings(@Param("timeLimit") LocalDateTime timeLimit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") String bookingId);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
            "FROM Booking b " +
            "WHERE b.showtimeId IN :showtimeIds " +
            "AND b.status IN :statuses")
    boolean existsActiveBookingByShowtimeIds(
            @Param("showtimeIds") List<String> showtimeIds,
            @Param("statuses") List<String> statuses
    );
}
