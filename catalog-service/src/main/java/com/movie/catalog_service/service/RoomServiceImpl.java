package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.RoomRequestDTO;
import com.movie.catalog_service.dto.request.SeatTypeUpdateRequestDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.dto.response.SeatMatrixResponseDTO;
import com.movie.catalog_service.dto.response.SeatResponseDTO;
import com.movie.catalog_service.entity.Cinema;
import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.Seat;
import com.movie.catalog_service.entity.ShowtimeStatus;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.CinemaRepository;
import com.movie.catalog_service.repository.RoomRepository;
import com.movie.catalog_service.repository.SeatRepository;
import com.movie.catalog_service.repository.ShowtimeRepository;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    private static final String BOOKING_SERVICE_BASE_URL = "http://localhost:8082/api/v1/booking";

    private static final Set<String> VALID_SEAT_TYPES = Set.of(
            "NORMAL",
            "VIP",
            "COUPLE",
            "MAINTENANCE",
            "EMPTY"
    );

    @Value("${app.internal-secret}")
    private String internalSecret;

    private boolean hasActiveBookingsInRoom(String roomId) {
        List<String> showtimeIds = showtimeRepository.findShowtimeIdsByRoomId(roomId);

        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return false;
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<List<String>> entity = new HttpEntity<>(showtimeIds, headers);

        ResponseEntity<Boolean> response = restTemplate.exchange(
                BOOKING_SERVICE_BASE_URL + "/internal/showtimes/has-active-bookings",
                HttpMethod.POST,
                entity,
                Boolean.class
        );

        return Boolean.TRUE.equals(response.getBody());
    }

    private String normalizeSeatType(String seatType) {
        if (seatType == null || seatType.isBlank()) {
            throw new APIException("Loại ghế không được để trống!");
        }

        String normalized = seatType.trim().toUpperCase();

        if (!VALID_SEAT_TYPES.contains(normalized)) {
            throw new APIException("Loại ghế không hợp lệ! Chỉ cho phép: NORMAL, VIP, COUPLE, MAINTENANCE, EMPTY");
        }

        return normalized;
    }

    private void validateRoomLayout(RoomRequestDTO request) {
        if (request.getTotalRows() == null || request.getTotalRows() < 1 || request.getTotalRows() > 26) {
            throw new APIException("Số hàng của phòng phải nằm trong khoảng 1 đến 26!");
        }

        if (request.getTotalColumns() == null || request.getTotalColumns() < 1 || request.getTotalColumns() > 50) {
            throw new APIException("Số cột của phòng phải nằm trong khoảng 1 đến 50!");
        }

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new APIException("Danh sách cấu hình ghế không được để trống!");
        }

        Set<String> usedGridPositions = new HashSet<>();
        Set<String> usedSeatLabels = new HashSet<>();

        for (RoomRequestDTO.SeatCreateRequest seatReq : request.getSeats()) {
            validateSingleSeatInLayout(seatReq, request.getTotalRows(), request.getTotalColumns());

            String gridKey = seatReq.getRowIndex() + "-" + seatReq.getColumnIndex();

            if (!usedGridPositions.add(gridKey)) {
                throw new APIException("Bị trùng tọa độ ghế tại rowIndex="
                        + seatReq.getRowIndex() + ", columnIndex=" + seatReq.getColumnIndex());
            }

            String seatType = normalizeSeatType(seatReq.getSeatType());

            if (!"EMPTY".equalsIgnoreCase(seatType)) {
                String labelKey = seatReq.getRowLabel().trim().toUpperCase()
                        + "-"
                        + seatReq.getColumnLabel().trim().toUpperCase();

                if (!usedSeatLabels.add(labelKey)) {
                    throw new APIException("Bị trùng mã ghế: "
                            + seatReq.getRowLabel() + seatReq.getColumnLabel());
                }
            }
        }
    }

    private void validateSingleSeatInLayout(
            RoomRequestDTO.SeatCreateRequest seatReq,
            Integer totalRows,
            Integer totalColumns
    ) {
        if (seatReq.getRowIndex() == null) {
            throw new APIException("rowIndex không được để trống!");
        }

        if (seatReq.getColumnIndex() == null) {
            throw new APIException("columnIndex không được để trống!");
        }

        if (seatReq.getRowIndex() < 0 || seatReq.getRowIndex() >= totalRows) {
            throw new APIException("rowIndex không hợp lệ: " + seatReq.getRowIndex()
                    + ". Giá trị hợp lệ là 0 đến " + (totalRows - 1));
        }

        if (seatReq.getColumnIndex() < 0 || seatReq.getColumnIndex() >= totalColumns) {
            throw new APIException("columnIndex không hợp lệ: " + seatReq.getColumnIndex()
                    + ". Giá trị hợp lệ là 0 đến " + (totalColumns - 1));
        }

        String seatType = normalizeSeatType(seatReq.getSeatType());

        if (!"EMPTY".equalsIgnoreCase(seatType)) {
            if (seatReq.getRowLabel() == null || seatReq.getRowLabel().isBlank()) {
                throw new APIException("Ghế thật phải có rowLabel!");
            }

            if (seatReq.getColumnLabel() == null || seatReq.getColumnLabel().isBlank()) {
                throw new APIException("Ghế thật phải có columnLabel!");
            }

            if (seatReq.getRowLabel().trim().length() > 2) {
                throw new APIException("rowLabel không được dài quá 2 ký tự!");
            }

            if (seatReq.getColumnLabel().trim().length() > 3) {
                throw new APIException("columnLabel không được dài quá 3 ký tự!");
            }
        }
    }

    private int toDatabaseGridIndex(Integer zeroBasedIndex) {
        return zeroBasedIndex + 1;
    }

    @Override
    @Transactional
    public RoomResponseDTO createRoom(RoomRequestDTO request) {
        validateRoomLayout(request);
        // 1. Kiểm tra rạp có tồn tại không
        Cinema cinema = cinemaRepository.findById(request.getCinemaId())
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", request.getCinemaId()));

        if (!cinema.getIsActive()) {
            throw new APIException("Rạp chiếu này đã dừng hoạt động, không thể tạo phòng chiếu mới!");
        }

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
                .peek(seatReq -> normalizeSeatType(seatReq.getSeatType()))
                .filter(seatReq -> !"EMPTY".equalsIgnoreCase(seatReq.getSeatType()))
                .map(seatReq -> {
                    Seat seat = new Seat();
                    seat.setRoom(savedRoom);

                    seat.setRowName(seatReq.getRowLabel());
                    seat.setSeatLabel(seatReq.getColumnLabel());
                    seat.setSeatType(normalizeSeatType(seatReq.getSeatType()));

                    seat.setGridRow(toDatabaseGridIndex(seatReq.getRowIndex()));
                    seat.setGridColumn(toDatabaseGridIndex(seatReq.getColumnIndex()));

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
        return roomRepository.findByCinemaId(cinemaId).stream().map(room -> {
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
        validateRoomLayout(request);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));

        if (hasActiveBookingsInRoom(roomId)) {
            throw new APIException("Không thể cập nhật phòng/sơ đồ ghế vì phòng này đã có khách đặt vé!");
        }

        // =========================================================================
        // 1. CHỈ CHẶN NẾU CÓ SUẤT CHIẾU TƯƠNG LAI ĐANG HOẠT ĐỘNG (KHÔNG PHẢI CANCELLED)
        // =========================================================================
        boolean hasActiveUpcomingShowtimes = showtimeRepository.existsByRoomIdAndStartTimeAfterAndStatusNot(
                roomId,
                LocalDateTime.now(),
                ShowtimeStatus.CANCELLED
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
            List<Seat> newSeats = request.getSeats().stream()
                    .peek(seatReq -> normalizeSeatType(seatReq.getSeatType()))
                    .filter(seatReq -> !"EMPTY".equalsIgnoreCase(seatReq.getSeatType()))
                    .map(seatReq -> {
                        Seat seat = new Seat();
                        seat.setRoom(room);
                        seat.setRowName(seatReq.getRowLabel());
                        seat.setSeatLabel(seatReq.getColumnLabel());
                        seat.setSeatType(normalizeSeatType(seatReq.getSeatType()));
                        seat.setGridRow(toDatabaseGridIndex(seatReq.getRowIndex()));
                        seat.setGridColumn(toDatabaseGridIndex(seatReq.getColumnIndex()));
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
    @Transactional
    public RoomResponseDTO deleteRoom(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));

        // Khi phòng dừng hoạt động, hủy toàn bộ suất chiếu tương lai của phòng.
        int cancelledCount = showtimeRepository.cancelFutureScheduledShowtimesByRoomId(
                roomId,
                LocalDateTime.now(),
                ShowtimeStatus.SCHEDULED,
                ShowtimeStatus.CANCELLED
        );

        System.out.println(">>> Đã hủy " + cancelledCount + " suất chiếu tương lai của phòng " + roomId);

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
        String seatType = normalizeSeatType(request.getSeatType());

        if ("EMPTY".equalsIgnoreCase(seatType)) {
            throw new APIException("Không thể cập nhật ghế thật thành EMPTY bằng API này. Hãy dùng API cập nhật sơ đồ phòng!");
        }

        List<Seat> seats = seatRepository.findAllById(request.getSeatIds());

        if (seats.isEmpty()) {
            throw new APIException("Không tìm thấy dữ liệu cho các ID ghế đã cung cấp!");
        }

        for (Seat seat : seats) {
            if (!Boolean.TRUE.equals(seat.getRoom().getIsActive())) {
                throw new APIException("Không thể cập nhật ghế thuộc phòng chiếu đã dừng hoạt động!");
            }

            seat.setSeatType(seatType);
        }

        List<Seat> updatedSeats = seatRepository.saveAll(seats);

        return updatedSeats.stream()
                .map(seat -> modelMapper.map(seat, SeatResponseDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoomResponseDTO reopenRoom(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room", "id", roomId));

        if (!Boolean.TRUE.equals(room.getCinema().getIsActive())) {
            throw new APIException("Không thể mở lại phòng vì rạp đang dừng hoạt động!");
        }

        room.setIsActive(true);

        Room savedRoom = roomRepository.save(room);

        RoomResponseDTO dto = modelMapper.map(savedRoom, RoomResponseDTO.class);
        dto.setCinemaName(savedRoom.getCinema().getName());
        return dto;
    }
}
