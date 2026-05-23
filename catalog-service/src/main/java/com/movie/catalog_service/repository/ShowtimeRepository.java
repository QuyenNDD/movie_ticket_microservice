package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Showtime;
import com.movie.catalog_service.entity.ShowtimeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime, String> {

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END " +
            "FROM Showtime s " +
            "WHERE s.room.id = :roomId " +
            "AND s.status = :status " +
            "AND s.startTime < :newEndTime " +
            "AND s.endTime > :newStartTime")
    boolean existsOverlappingShowtime(
            @Param("roomId") String roomId,
            @Param("newStartTime") LocalDateTime newStartTime,
            @Param("newEndTime") LocalDateTime newEndTime,
            @Param("status") ShowtimeStatus status
    );

    @Query("SELECT s FROM Showtime s " +
            "WHERE s.room.cinema.id = :cinemaId " +
            "AND s.status = :status " +
            "AND s.movie.status = 'ACTIVE' " +
            "AND s.room.isActive = true " +
            "AND s.room.cinema.isActive = true " +
            "AND DATE(s.startTime) = DATE(:date) " +
            "ORDER BY s.startTime ASC")
    List<Showtime> findShowtimesByCinemaAndDate(
            @Param("cinemaId") String cinemaId,
            @Param("date") LocalDateTime date,
            @Param("status") ShowtimeStatus status
    );

    @Query("SELECT s FROM Showtime s " +
            "WHERE s.movie.id = :movieId " +
            "AND s.status = :status " +
            "AND s.movie.status = 'ACTIVE' " +
            "AND s.room.isActive = true " +
            "AND s.room.cinema.isActive = true " +
            "AND DATE(s.startTime) = DATE(:date) " +
            "ORDER BY s.room.cinema.name ASC, s.startTime ASC")
    List<Showtime> findShowtimesByMovieAndDate(
            @Param("movieId") String movieId,
            @Param("date") LocalDateTime date,
            @Param("status") ShowtimeStatus status
    );

    boolean existsByRoomIdAndStartTimeAfterAndStatusNot(
            String roomId,
            LocalDateTime currentTime,
            ShowtimeStatus status
    );

    @Query("SELECT s.id FROM Showtime s WHERE s.room.id = :roomId")
    List<String> findShowtimeIdsByRoomId(@Param("roomId") String roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Showtime s SET s.status = :cancelledStatus " +
            "WHERE s.movie.id = :movieId " +
            "AND s.startTime > :now " +
            "AND s.status = :scheduledStatus")
    int cancelFutureScheduledShowtimesByMovieId(
            @Param("movieId") String movieId,
            @Param("now") LocalDateTime now,
            @Param("scheduledStatus") ShowtimeStatus scheduledStatus,
            @Param("cancelledStatus") ShowtimeStatus cancelledStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Showtime s SET s.status = :cancelledStatus " +
            "WHERE s.room.id = :roomId " +
            "AND s.startTime > :now " +
            "AND s.status = :scheduledStatus")
    int cancelFutureScheduledShowtimesByRoomId(
            @Param("roomId") String roomId,
            @Param("now") LocalDateTime now,
            @Param("scheduledStatus") ShowtimeStatus scheduledStatus,
            @Param("cancelledStatus") ShowtimeStatus cancelledStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Showtime s SET s.status = :cancelledStatus " +
            "WHERE s.room.cinema.id = :cinemaId " +
            "AND s.startTime > :now " +
            "AND s.status = :scheduledStatus")
    int cancelFutureScheduledShowtimesByCinemaId(
            @Param("cinemaId") String cinemaId,
            @Param("now") LocalDateTime now,
            @Param("scheduledStatus") ShowtimeStatus scheduledStatus,
            @Param("cancelledStatus") ShowtimeStatus cancelledStatus
    );
}