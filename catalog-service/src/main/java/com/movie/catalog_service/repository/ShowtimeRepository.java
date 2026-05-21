package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
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
            "AND s.status != 'CANCELLED' " +
            "AND s.startTime < :newEndTime " +
            "AND s.endTime > :newStartTime")
    boolean existsOverlappingShowtime(
            @Param("roomId") String roomId,
            @Param("newStartTime") LocalDateTime newStartTime,
            @Param("newEndTime") LocalDateTime newEndTime
    );

    @Query("SELECT s FROM Showtime s " +
            "WHERE s.room.cinema.id = :cinemaId " +
            "AND s.status != 'CANCELLED' " + // Không lấy suất bị hủy
            "AND DATE(s.startTime) = DATE(:date) " + // Chỉ lấy đúng ngày
            "ORDER BY s.startTime ASC") // Sắp xếp giờ chiếu từ sáng đến tối
    List<Showtime> findShowtimesByCinemaAndDate(@Param("cinemaId") String cinemaId, @Param("date") LocalDateTime date);

    // 2. Lấy danh sách suất chiếu của 1 PHIM theo NGÀY
    @Query("SELECT s FROM Showtime s " +
            "WHERE s.movie.id = :movieId " +
            "AND s.status != 'CANCELLED' " +
            "AND DATE(s.startTime) = DATE(:date) " +
            "ORDER BY s.room.cinema.name ASC, s.startTime ASC") // Sắp xếp theo tên rạp trước, giờ chiếu sau
    List<Showtime> findShowtimesByMovieAndDate(@Param("movieId") String movieId, @Param("date") LocalDateTime date);

    boolean existsByRoomIdAndStartTimeAfterAndStatusNot(String roomId, LocalDateTime currentTime, String status);
}
