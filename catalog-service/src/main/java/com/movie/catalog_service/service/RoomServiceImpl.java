package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.RoomRequestDTO;
import com.movie.catalog_service.dto.request.SeatTypeUpdateRequestDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.dto.response.SeatMatrixResponseDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;
import com.movie.catalog_service.entity.Cinema;
import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.Seat;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.CinemaRepository;
import com.movie.catalog_service.repository.RoomRepository;
import com.movie.catalog_service.repository.SeatRepository;
import com.movie.catalog_service.repository.ShowtimeRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
    @Autowired
    ShowtimeRepository showtimeRepository;

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
        room.setTotalSeats(0);
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

        RoomResponseDTO response = new RoomResponseDTO();
        response.setId(room.getId());
        response.setName(room.getName());
        if (room.getSeats() != null && !room.getSeats().isEmpty()) {

            // 1. Nhóm các ghế lại theo tọa độ vật lý trục Y (gridRow)
            // Dùng TreeMap để đảm bảo Hàng 0 luôn đứng trước Hàng 1, Hàng 2...
            Map<Integer, List<Seat>> groupedByGridRow = room.getSeats().stream()
                    .collect(Collectors.groupingBy(
                            Seat::getGridRow,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            // 2. Map sang mảng 2D
            List<List<SeatMatrixResponseDTO>> seatMatrix = groupedByGridRow.values().stream()
                    .map(seatsInPhysicalRow -> {
                        return seatsInPhysicalRow.stream()
                                // Sắp xếp các ghế trong cùng 1 hàng theo trục X (từ trái qua phải)
                                .sorted(Comparator.comparingInt(Seat::getGridColumn))
                                .map(seat -> {
                                    SeatMatrixResponseDTO dto = new SeatMatrixResponseDTO();
                                    dto.setId(seat.getId());
                                    dto.setRow(seat.getRowName());     // VD: "A"
                                    dto.setCol(seat.getSeatLabel());   // VD: "1"
                                    dto.setType(seat.getSeatType());   // VD: "VIP"
                                    return dto;
                                })
                                .collect(Collectors.toList());
                    })
                    .collect(Collectors.toList());

            response.setSeatMatrix(seatMatrix);
        }
        return response;
    }

    @Override
    public RoomResponseDTO updateRoom(String roomId, RoomRequestDTO request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));

        // =========================================================================
        // 1. CHỈ CHẶN NẾU CÓ SUẤT CHIẾU TƯƠNG LAI ĐANG HOẠT ĐỘNG (KHÔNG PHẢI CANCELLED)
        // =========================================================================
        boolean hasActiveUpcomingShowtimes = showtimeRepository.existsByRoomIdAndStartTimeAfterAndStatusNot(
                roomId,
                LocalDateTime.now(),
                "CANCELLED"
        );

        if (hasActiveUpcomingShowtimes) {
            throw new APIException("Không thể cập nhật sơ đồ ghế! Phòng chiếu này đang có lịch chiếu hoạt động chưa diễn ra.");
        }

        // 2. Kiểm tra trùng tên phòng trong cùng 1 rạp
        if (!room.getName().equals(request.getName()) &&
                roomRepository.existsByCinemaIdAndName(room.getCinema().getId(), request.getName())) {
            throw new APIException("Tên phòng này đã tồn tại trong cụm rạp!");
        }
        // 2. Cập nhật thông tin cơ bản của Room
        room.setName(request.getName());
        room.setRowCount(request.getTotalRows());
        room.setColumnCount(request.getTotalColumns());

        // ==========================================
        // 3. LOGIC CẬP NHẬT GHẾ (CLEAR & REPLACE)
        // ==========================================

        // 3.1. Xóa toàn bộ liên kết ghế cũ.
        // (Nhờ orphanRemoval = true, Hibernate sẽ tự sinh ra câu lệnh DELETE dưới Database)
        room.getSeats().clear();

        // 3.2. Tạo danh sách ghế mới từ Request và đưa vào Room
        if (request.getSeats() != null && !request.getSeats().isEmpty()) {
            List<Seat> newSeats = request.getSeats().stream().map(seatReq -> {
                Seat seat = new Seat();
                seat.setRoom(room); // Bắt buộc phải có để map quan hệ 2 chiều
                seat.setRowName(seatReq.getRowLabel());
                seat.setSeatLabel(seatReq.getColumnLabel());
                seat.setSeatType(seatReq.getSeatType());
                seat.setGridRow(seatReq.getRowIndex());
                seat.setGridColumn(seatReq.getColumnIndex());
                return seat;
            }).collect(Collectors.toList());

            room.getSeats().addAll(newSeats);
        }

        // (Tùy chọn) Nếu Entity Room của bạn có trường lưu tổng số ghế, cập nhật luôn ở đây:
        // room.setTotalSeats(room.getSeats().size());

        // 4. Lưu Room (Hibernate sẽ tự động Insert các Seat mới nhờ CascadeType.ALL)
        Room updatedRoom = roomRepository.save(room);

        // ==========================================
        // 5. MAP RESPONSE TRẢ VỀ MA TRẬN GHẾ CHO FE
        // ==========================================
        RoomResponseDTO dto = modelMapper.map(updatedRoom, RoomResponseDTO.class);
        dto.setCinemaName(updatedRoom.getCinema().getName());

        // Dùng lại thuật toán gom nhóm mảng 2D mà chúng ta đã làm ở hàm getRoomById
        if (updatedRoom.getSeats() != null && !updatedRoom.getSeats().isEmpty()) {
            Map<Integer, List<Seat>> groupedByGridRow = updatedRoom.getSeats().stream()
                    .collect(Collectors.groupingBy(
                            Seat::getGridRow,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            List<List<SeatMatrixResponseDTO>> seatMatrix = groupedByGridRow.values().stream()
                    .map(seatsInPhysicalRow -> seatsInPhysicalRow.stream()
                            .sorted(Comparator.comparingInt(Seat::getGridColumn))
                            .map(seat -> {
                                SeatMatrixResponseDTO seatDto = new SeatMatrixResponseDTO();
                                seatDto.setId(seat.getId());
                                seatDto.setRow(seat.getRowName());
                                seatDto.setCol(seat.getSeatLabel());
                                seatDto.setType(seat.getSeatType());
                                return seatDto;
                            })
                            .collect(Collectors.toList())
                    )
                    .collect(Collectors.toList());

            dto.setSeatMatrix(seatMatrix);
        }

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
