package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<Seat, String> {
    List<Seat> findByRoomIdOrderByRowNameAscSeatNumberAsc(String roomId);
}
