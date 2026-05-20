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
    @Transactional
    public RoomResponseDTO createRoom(RoomRequestDTO request) {
        // 1. Kiểm tra rạp có tồn tại không
        Cinema cinema = cinemaRepository.findById(request.getCinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", request.getCinemaId()));

        // 2. Kiểm tra trùng tên phòng
        if (roomRepository.existsByCinemaIdAndName(request.getCinemaId(), request.getName())) {
            throw new APIException("Phòng chiếu " + request.getName() + " đã tồn tại trong cụm rạp này!");
        }

        // 3. Khởi tạo phòng
        Room room = new Room();
        room.setCinema(cinema);
        room.setName(request.getName()); // Sẽ lấy được giá trị nhờ @JsonProperty("roomName") ở DTO
        room.setRowCount(request.getTotalRows());
        room.setColumnCount(request.getTotalColumns());
        room.setIsActive(true);

        // Lưu tạm phòng xuống DB để lấy ID gán cho các ghế
        Room savedRoom = roomRepository.save(room);

        // ==========================================================
        // 4. XỬ LÝ LỌC VÀ LƯU DANH SÁCH GHẾ
        // ==========================================================
        List<Seat> seatsToSave = request.getSeats().stream()
                // BƯỚC 4.1: Vứt bỏ toàn bộ những ô là lối đi (EMPTY)
                .filter(seatReq -> !"EMPTY".equalsIgnoreCase(seatReq.getSeatType()))

                // BƯỚC 4.2: Chuyển đổi từ DTO sang Entity
                .map(seatReq -> {
                    Seat seat = new Seat();
                    seat.setRoom(savedRoom); // Trỏ về ID phòng vừa tạo

                    // Map trực tiếp các nhãn (Label)
                    seat.setRowName(seatReq.getRowLabel());     // Ví dụ: "A", "B"
                    seat.setSeatLabel(seatReq.getColumnLabel());// Ví dụ: "01", "01-02"
                    seat.setSeatType(seatReq.getSeatType());    // Ví dụ: "SINGLE", "DOUBLE"

                    // Map tọa độ: CỘNG 1 để khớp với hệ tọa độ của CSS Grid (bắt đầu từ 1)
                    seat.setGridRow(seatReq.getRowIndex() + 1);
                    seat.setGridColumn(seatReq.getColumnIndex() + 1);

                    return seat;
                }).collect(Collectors.toList());

        // 5. Lưu toàn bộ ghế thật xuống Database
        seatRepository.saveAll(seatsToSave);

        // 6. Cập nhật lại tổng số ghế thực tế (chỉ đếm ghế thật, không đếm lối đi)
        savedRoom.setTotalSeats(seatsToSave.size());
        roomRepository.save(savedRoom); // Update DB lần 2

        // 7. Map kết quả trả về cho Frontend
        RoomResponseDTO response = modelMapper.map(savedRoom, RoomResponseDTO.class);
        response.setCinemaName(cinema.getName());
        response.setTotalSeats(savedRoom.getTotalSeats()); // Trả về số ghế thực tế vừa cập nhật

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
        return seatRepository.findByRoomIdOrderByGridRowAscGridColumnAsc(roomId).stream()
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
