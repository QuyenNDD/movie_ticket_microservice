package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.RoomRequestDTO;
import com.movie.catalog_service.dto.request.SeatTypeUpdateRequestDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;
import com.movie.catalog_service.entity.Cinema;
import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.Seat;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.CinemaRepository;
import com.movie.catalog_service.repository.RoomRepository;
import com.movie.catalog_service.repository.SeatRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoomServiceImpl implements RoomService{
    @Autowired
    RoomRepository roomRepository;
    @Autowired
    CinemaRepository cinemaRepository;
    @Autowired
    SeatRepository seatRepository;
    @Autowired
    ModelMapper modelMapper;

    @Override
    @Transactional // Đảm bảo nếu lỗi sinh ghế thì rollback không lưu phòng
    public RoomResponseDTO createRoom(RoomRequestDTO request) {
        // 1. Kiểm tra rạp có tồn tại không
        Cinema cinema = cinemaRepository.findById(request.getCinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", request.getCinemaId()));

        // 2. Kiểm tra trùng tên phòng
        if (roomRepository.existsByCinemaIdAndName(request.getCinemaId(), request.getName())) {
            throw new APIException("Phòng chiếu " + request.getName() + " đã tồn tại trong cụm rạp này!");
        }

        // 3. Tạo phòng
        Room room = new Room();
        room.setCinema(cinema);
        room.setName(request.getName());
        room.setRowCount(request.getRowCount());
        room.setColumnCount(request.getColumnCount());
        room.setTotalSeats(request.getRowCount() * request.getColumnCount()); // Tự tính
        room.setIsActive(true);

        Room savedRoom = roomRepository.save(room);

        List<Seat> seatsToSave = new ArrayList<>();

        for (int i = 0; i < request.getRowCount(); i++) {
            String rowName = String.valueOf((char) ('A' + i));

            for (int j = 1; j <= request.getColumnCount(); j++) {
                Seat seat = new Seat();
                seat.setRoom(savedRoom);
                seat.setRowName(rowName);
                seat.setSeatNumber(j);
                seat.setSeatType("NORMAL");
                seatsToSave.add(seat);
            }
        }
        seatRepository.saveAll(seatsToSave);

        RoomResponseDTO response = modelMapper.map(savedRoom, RoomResponseDTO.class);
        response.setCinemaName(cinema.getName());
        return response;
    }

    @Override
    public List<RoomResponseDTO> getRoomsByCinemaId(String cinemaId) {
        return roomRepository.findByCinemaIdAndIsActiveTrue(cinemaId).stream().map(room -> {
            RoomResponseDTO dto = modelMapper.map(room, RoomResponseDTO.class);
            dto.setCinemaName(room.getCinema().getName());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public RoomResponseDTO getRoomById(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
        RoomResponseDTO dto = modelMapper.map(room, RoomResponseDTO.class);
        dto.setCinemaName(room.getCinema().getName());
        return dto;
    }

    @Override
    public RoomResponseDTO updateRoom(String roomId, RoomRequestDTO request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));

        if (!room.getName().equals(request.getName()) &&
                roomRepository.existsByCinemaIdAndName(room.getCinema().getId(), request.getName())) {
            throw new APIException("Tên phòng này đã tồn tại trong cụm rạp!");
        }

        room.setName(request.getName());
        Room updatedRoom = roomRepository.save(room);

        RoomResponseDTO dto = modelMapper.map(updatedRoom, RoomResponseDTO.class);
        dto.setCinemaName(updatedRoom.getCinema().getName());
        return dto;
    }

    @Override
    public RoomResponseDTO deleteRoom(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));
        room.setIsActive(false);
        Room savedRoom = roomRepository.save(room);
        RoomResponseDTO dto = modelMapper.map(savedRoom, RoomResponseDTO.class);
        dto.setCinemaName(savedRoom.getCinema().getName());

        return dto;
    }

    @Override
    public List<SeatResponseDTO> getSeatsByRoomId(String roomId) {
        return seatRepository.findByRoomIdOrderByRowNameAscSeatNumberAsc(roomId).stream()
                .map(seat -> modelMapper.map(seat, SeatResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<SeatResponseDTO> updateSeatTypes(SeatTypeUpdateRequestDTO request) {
        List<Seat> seats = seatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new APIException("Không tìm thấy dữ liệu cho các ID ghế đã cung cấp!");
        }

        for (Seat seat : seats) {
            seat.setSeatType(request.getSeatType());
        }
        List<Seat> updatedSeats = seatRepository.saveAll(seats);

        return updatedSeats.stream()
                .map(seat -> modelMapper.map(seat, SeatResponseDTO.class))
                .collect(Collectors.toList());
    }
}
