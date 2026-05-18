package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.request.RoomRequestDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;
import com.movie.catalog_service.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/rooms")
public class RoomController {
    @Autowired
    RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponseDTO> createRoom(@Valid @RequestBody RoomRequestDTO request) {
        return new ResponseEntity<>(roomService.createRoom(request), HttpStatus.CREATED);
    }

    @GetMapping("/cinema/{cinemaId}")
    public ResponseEntity<List<RoomResponseDTO>> getRoomsByCinemaId(@PathVariable String cinemaId) {
        return new ResponseEntity<>(roomService.getRoomsByCinemaId(cinemaId), HttpStatus.OK);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponseDTO> getRoomById(@PathVariable String roomId) {
        return new ResponseEntity<>(roomService.getRoomById(roomId), HttpStatus.OK);
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<RoomResponseDTO> updateRoom(
            @PathVariable String roomId,
            @Valid @RequestBody RoomRequestDTO request) {
        return new ResponseEntity<>(roomService.updateRoom(roomId, request), HttpStatus.OK);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<RoomResponseDTO> deleteRoom(@PathVariable String roomId) {
        RoomResponseDTO deletedRoom = roomService.deleteRoom(roomId);
        return new ResponseEntity<>(deletedRoom, HttpStatus.OK);
    }

    @GetMapping("/{roomId}/seats")
    public ResponseEntity<List<SeatResponseDTO>> getSeatsByRoomId(@PathVariable String roomId) {
        return new ResponseEntity<>(roomService.getSeatsByRoomId(roomId), HttpStatus.OK);
    }
}
