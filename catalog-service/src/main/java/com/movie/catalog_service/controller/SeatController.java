package com.movie.catalog_service.controller;

import com.movie.catalog_service.dto.request.SeatTypeUpdateRequestDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;
import com.movie.catalog_service.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/seats")
public class SeatController {

    @Autowired
    private RoomService roomService;

    @PutMapping("/type")
    public ResponseEntity<List<SeatResponseDTO>> updateSeatTypes(@Valid @RequestBody SeatTypeUpdateRequestDTO request) {
        List<SeatResponseDTO> updatedSeats = roomService.updateSeatTypes(request);
        return new ResponseEntity<>(updatedSeats, HttpStatus.OK);
    }
}