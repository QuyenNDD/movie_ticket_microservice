package com.movie.booking_service.service;

import com.movie.booking_service.client.CatalogClient;
import com.movie.booking_service.config.ModelMapperConfig;
import com.movie.booking_service.dto.*;
import com.movie.booking_service.entity.Booking;
import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.BookingSnack;
import com.movie.booking_service.repository.BookingRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class BookingServiceImpl implements BookingService{
    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ModelMapper modelMapper; // TIÊM MODELMAPPER VÀO ĐÂY

    @Autowired
    private RestTemplate restTemplate;

    @Transactional
    public BookingResponseDTO holdSeats(String userId, BookingRequestDTO request) {
        String showtimeId = request.getShowtimeId();
        String redisKeyPrefix = "lock:showtime:" + showtimeId + ":seat:";

        // 1. CHECK REDIS: Kiểm tra ghế có đang bị người khác giữ không?
        for (BookingRequestDTO.SeatRequest seat : request.getSeats()) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKeyPrefix + seat.getSeatId()))) {
                throw new RuntimeException("Ghế " + seat.getSeatId() + " vừa có người chọn mất rồi!");
            }
        }

        // 2. CHECK DATABASE: Kiểm tra ghế đã bán chưa?
        List<String> seatIds = request.getSeats().stream().map(BookingRequestDTO.SeatRequest::getSeatId).toList();
        if (bookingRepository.checkIfSeatsArePaid(showtimeId, seatIds)) {
             throw new RuntimeException("Một trong các ghế bạn chọn đã được thanh toán!");
        }

        // 3. MAP DỮ LIỆU & TÍNH TỔNG TIỀN
        Booking booking = modelMapper.map(request, Booking.class);
        booking.setUserId(userId);
        booking.setStatus("PENDING"); // Đặt trạng thái chờ thanh toán

        double totalPrice = 0.0; // Biến tính tổng tiền

        // 3.1 Xử lý Ghế
        List<BookingSeat> bookingSeats = request.getSeats().stream().map(seatReq -> {
            BookingSeat seat = modelMapper.map(seatReq, BookingSeat.class);
            seat.setBooking(booking);
            redisTemplate.opsForValue().set(redisKeyPrefix + seatReq.getSeatId(), "LOCKED", 300, TimeUnit.SECONDS);
            return seat;
        }).collect(Collectors.toList());

        totalPrice = request.getSeats().stream()
                .mapToDouble(BookingRequestDTO.SeatRequest::getPrice)
                .sum();

        booking.setTotalPrice(totalPrice);

        // 3.2 Xử lý Bắp nước (Nếu có)
        if (request.getSnacks() != null && !request.getSnacks().isEmpty()) {
            List<BookingSnack> bookingSnacks = request.getSnacks().stream().map(snackReq -> {
                BookingSnack snack = modelMapper.map(snackReq, BookingSnack.class);
                snack.setBooking(booking);
                return snack;
            }).collect(Collectors.toList());

            booking.setBookingSnacks(bookingSnacks);

            // Cộng tiền bắp nước (Giá * Số lượng)
            totalPrice += request.getSnacks().stream()
                    .mapToDouble(s -> s.getPrice() * s.getQuantity())
                    .sum();
        }

        booking.setTotalPrice(totalPrice); // Chốt tổng tiền lưu vào DB

        // 4. LƯU DATABASE
        Booking savedBooking = bookingRepository.save(booking);

        // 5. TRẢ KẾT QUẢ CHO FRONTEND
        return BookingResponseDTO.builder()
                .bookingId(savedBooking.getId()) // Lấy ID từ biến mới
                .status("PENDING")
                .message("Giữ chỗ thành công! Vui lòng thanh toán trong 5 phút.")
                .totalPrice(savedBooking.getTotalPrice())
                .expiresInSeconds(300)
                .build();
    }

    @Override
    public RoomSeatMatrixResponseDTO getSeatsForShowtime(String showtimeId) {
        String showtimeInfoUrl = "http://localhost:8080/api/v1/catalog/showtimes/" + showtimeId;
        ShowtimeResponseDTO showtimeInfo = restTemplate.getForObject(showtimeInfoUrl, ShowtimeResponseDTO.class);

        if (showtimeInfo == null || showtimeInfo.getRoomId() == null) {
            throw new RuntimeException("Lấy thông tin suất chiếu từ Catalog thất bại!");
        }

        String roomId = showtimeInfo.getRoomId();
        String roomName = showtimeInfo.getRoomName(); // Đã lấy được tên phòng từ đây!

        // ==========================================
        // 2. LẤY BẢN ĐỒ GHẾ GỐC TỪ CATALOG
        // ==========================================
        String catalogUrl = "http://localhost:8080/api/v1/catalog/rooms/internal/" + roomId + "/seats";
        SeatStatusResponseDTO[] rawSeatsArray = restTemplate.getForObject(catalogUrl, SeatStatusResponseDTO[].class);

        if (rawSeatsArray == null || rawSeatsArray.length == 0) {
            // Nếu rạp chưa xếp ghế, trả về đối tượng rỗng an toàn cho FE
            RoomSeatMatrixResponseDTO emptyResponse = new RoomSeatMatrixResponseDTO();
            emptyResponse.setRoomId(roomId);
            emptyResponse.setRoomName(roomName);
            emptyResponse.setSeatMatrix(new ArrayList<>());
            return emptyResponse;
        }
        List<SeatStatusResponseDTO> seats = Arrays.asList(rawSeatsArray);

        // ==========================================
        // 3. QUÉT TRẠNG THÁI (ĐỎ - VÀNG - XANH)
        // ==========================================
        List<String> paidSeatIds = bookingRepository.findPaidSeatIdsByShowtime(showtimeId);

        String redisKeyPrefix = "lock:showtime:" + showtimeId + ":seat:";
        Set<String> lockedSeatKeys = redisTemplate.keys(redisKeyPrefix + "*");

        // Biến để tìm ra tổng số hàng và cột (Không cần gọi API nữa)
        int maxGridRow = 0;
        int maxGridColumn = 0;

        for (SeatStatusResponseDTO seat : seats) {
            String seatKey = redisKeyPrefix + seat.getId();

            // Tô màu
            if (paidSeatIds.contains(seat.getId())) {
                seat.setStatus("PAID");
            } else if (lockedSeatKeys != null && lockedSeatKeys.contains(seatKey)) {
                seat.setStatus("LOCKED");
            } else {
                seat.setStatus("AVAILABLE");
            }

            // Tính số hàng/cột lớn nhất để trả về cho FE
            if (seat.getGridRow() > maxGridRow) maxGridRow = seat.getGridRow();
            if (seat.getGridColumn() > maxGridColumn) maxGridColumn = seat.getGridColumn();
        }

        // ==========================================
        // 4. BIẾN HÌNH (1 CHIỀU -> MATRIX 2 CHIỀU)
        // ==========================================

        // Gom nhóm theo hàng (gridRow) và giữ thứ tự tăng dần bằng TreeMap
        Map<Integer, List<SeatStatusResponseDTO>> groupedByRow = seats.stream()
                .collect(Collectors.groupingBy(
                        SeatStatusResponseDTO::getGridRow,
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<List<SeatMatrixItemDTO>> matrix = new ArrayList<>();

        // Lặp qua từng hàng
        for (List<SeatStatusResponseDTO> rowSeats : groupedByRow.values()) {

            // Sắp xếp các ghế trong 1 hàng từ trái sang phải
            rowSeats.sort(Comparator.comparingInt(SeatStatusResponseDTO::getGridColumn));

            // Map sang format DTO FE muốn
            List<SeatMatrixItemDTO> feRow = rowSeats.stream().map(s -> {
                SeatMatrixItemDTO dto = new SeatMatrixItemDTO();
                dto.setId(s.getId());
                dto.setRow(s.getRowName());
                dto.setCol(s.getSeatLabel());
                dto.setType(s.getSeatType());
                dto.setStatus(s.getStatus());
                dto.setGridRow(s.getGridRow()); // Vẫn trả về để FE làm CSS Grid
                dto.setGridColumn(s.getGridColumn());
                return dto;
            }).collect(Collectors.toList());

            matrix.add(feRow);
        }

        // ==========================================
        // 5. ĐÓNG GÓI VÀ TRẢ KẾT QUẢ CUỐI CÙNG
        // ==========================================
        RoomSeatMatrixResponseDTO finalResponse = new RoomSeatMatrixResponseDTO();
        finalResponse.setRoomId(roomId);
        finalResponse.setRoomName(roomName);
        finalResponse.setTotalRows(maxGridRow);       // FE nhận được đúng kích thước thực tế
        finalResponse.setTotalColumns(maxGridColumn); // FE nhận được đúng kích thước thực tế
        finalResponse.setSeatMatrix(matrix);

        return finalResponse;
    }

    @Override
    @Transactional
    public BookingResponseDTO confirmPayment(String userId, String bookingId) {
        // 1. Tìm hóa đơn trong Database
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn này!"));

        // ==========================================
        // 2. KIỂM TRA BẢO MẬT (Chính chủ)
        // ==========================================
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thanh toán hóa đơn của người khác!");
        }

        // 3. Kiểm tra xem hóa đơn có đang ở trạng thái chờ không?
        if (!"PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Hóa đơn này đã được xử lý hoặc đã hết thời gian giữ chỗ!");
        }

        // 4. Chốt đơn: Đổi trạng thái thành ĐÃ THANH TOÁN
        booking.setStatus("PAID");
        bookingRepository.save(booking);

        // 5. GIẢI PHÓNG REDIS: Xóa khóa để ghế chính thức chuyển sang màu Đỏ (đã bán)
        String showtimeId = booking.getShowtimeId();
        String redisKeyPrefix = "lock:showtime:" + showtimeId + ":seat:";

        for (BookingSeat seat : booking.getBookingSeats()) {
            redisTemplate.delete(redisKeyPrefix + seat.getSeatId());
        }

        // 6. Trả kết quả về
        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .status("PAID")
                .message("Thanh toán thành công! Vé của bạn đã được xác nhận.")
                .totalPrice(booking.getTotalPrice())
                .build();
    }

    @Override
    @Transactional
    public BookingResponseDTO cancelBooking(String userId, String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin hóa đơn này!"));

        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền hủy hóa đơn của người khác!");
        }

        if (!"PENDING".equals(booking.getStatus())) {
            throw new RuntimeException("Hóa đơn này không ở trạng thái chờ thanh toán, không thể hủy!");
        }

        // 1. Cập nhật DB
        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);

        // 2. Giải phóng Redis tức thì
        String redisKeyPrefix = "lock:showtime:" + booking.getShowtimeId() + ":seat:";
        for (BookingSeat seat : booking.getBookingSeats()) {
            redisTemplate.delete(redisKeyPrefix + seat.getSeatId());
        }

        // 3. Trả về DTO cấu trúc sạch sẽ cho FE dễ xử lý
        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .status("CANCELLED")
                .message("Hủy hóa đơn và nhả ghế thành công!")
                .build();
    }

    @Override
    public BookingResponseDTO getBookingDetails(String userId, String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin hóa đơn này!"));

        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Lỗi bảo mật: Không có quyền truy cập!");
        }

        // Tính toán số giây còn lại (Thời hạn là 5 phút = 300 giây)
        long elapsedSeconds = Duration.between(booking.getBookingTime(), LocalDateTime.now()).getSeconds();
        long remainingSeconds = 300 - elapsedSeconds;

        // Nếu quá 5 phút mà Cron Job chưa kịp quét, ta chủ động ép về 0 giây
        if (remainingSeconds < 0 || "CANCELLED".equals(booking.getStatus())) {
            remainingSeconds = 0;
        }

        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .expiresInSeconds(remainingSeconds) // Trả về số giây còn lại cho FE
                .message("Lấy thông tin hóa đơn thành công.")
                .build();
    }
}
