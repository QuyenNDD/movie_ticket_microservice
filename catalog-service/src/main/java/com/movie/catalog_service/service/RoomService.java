package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.RoomRequestDTO;
import com.movie.catalog_service.dto.request.SeatTypeUpdateRequestDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;

import java.util.List;

public interface RoomService {
    RoomResponseDTO createRoom(RoomRequestDTO request);
    List<RoomResponseDTO> getRoomsByCinemaId(String cinemaId);
    RoomResponseDTO getRoomById(String roomId);
    RoomResponseDTO updateRoom(String roomId, RoomRequestDTO request);
    RoomResponseDTO deleteRoom(String roomId);
    List<SeatResponseDTO> getSeatsByRoomId(String roomId);
    List<SeatResponseDTO> updateSeatTypes(SeatTypeUpdateRequestDTO request);
}
