package com.movie.catalog_service.repository;

import com.movie.catalog_service.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    List<Room> findByCinemaIdAndIsActiveTrue(String cinemaId);
    List<Room> findByCinemaId(String cinemaId);
    boolean existsByCinemaIdAndName(String cinemaId, String name);
}
