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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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

    private static final long SEAT_HOLD_TTL_SECONDS = 600; // 10 phút
    private static final long BOOKING_CUTOFF_MINUTES = 15; // Khóa bán vé trước giờ chiếu 15 phút
    private static final int MAX_SEATS_PER_BOOKING = 8;
    private static final int MAX_SNACK_QUANTITY_PER_ITEM = 20;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Value("${app.catalog-service-url:http://localhost:8081/api/v1/catalog}")
    private String catalogServiceUrl;

    @Transactional
    public BookingResponseDTO holdSeats(String userId, BookingRequestDTO request) {
        String showtimeId = request.getShowtimeId();
        String redisKeyPrefix = "lock:showtime:" + showtimeId + ":seat:";

        if (showtimeId == null || showtimeId.isBlank()) {
            throw new RuntimeException("Thiếu thông tin suất chiếu!");
        }

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ít nhất 1 ghế!");
        }

        // 1. Check suất chiếu còn mở bán không
        ShowtimeResponseDTO showtimeInfo = getAndValidateShowtimeForBooking(showtimeId);

        // 2. Lấy danh sách seatId
        List<String> seatIds = request.getSeats()
                .stream()
                .map(BookingRequestDTO.SeatRequest::getSeatId)
                .toList();

        // 3. Check trùng ghế trong cùng request
        long distinctSeatCount = seatIds.stream().distinct().count();

        if (distinctSeatCount != seatIds.size()) {
            throw new RuntimeException("Danh sách ghế có ghế bị trùng!");
        }

        // 4. Check DB: ghế đã thanh toán chưa
        if (bookingRepository.checkIfSeatsArePaid(showtimeId, seatIds)) {
            throw new RuntimeException("Một trong các ghế bạn chọn đã được thanh toán!");
        }

        // 5. Vá nhóm 9:
        // - Check ghế có thuộc đúng phòng của suất chiếu không
        // - Check ghế PAID/LOCKED/MAINTENANCE
        // - Rule COUPLE
        // - Không để lại ghế trống lẻ cô lập
        validateSeatSelectionRules(showtimeId, showtimeInfo.getRoomId(), seatIds);

        List<String> lockedKeys = new ArrayList<>();

        try {
            // 6. Lock Redis bằng setIfAbsent để tránh 2 người giữ cùng ghế
            for (String seatId : seatIds) {
                String redisKey = redisKeyPrefix + seatId;

                Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                        redisKey,
                        "LOCKED",
                        SEAT_HOLD_TTL_SECONDS,
                        TimeUnit.SECONDS
                );

                if (!Boolean.TRUE.equals(locked)) {
                    throw new RuntimeException("Ghế " + seatId + " vừa có người chọn mất rồi!");
                }

                lockedKeys.add(redisKey);
            }

            // 7. Tạo booking
            Booking booking = modelMapper.map(request, Booking.class);
            booking.setUserId(userId);
            booking.setStatus("PENDING");

            double totalPrice = 0.0;

            // 7.1 Xử lý ghế
            List<BookingSeat> bookingSeats = new ArrayList<>();

            for (BookingRequestDTO.SeatRequest seatReq : request.getSeats()) {
                Double seatPrice = getSeatPriceFromCatalog(showtimeId, seatReq.getSeatId());

                BookingSeat seat = new BookingSeat();
                seat.setSeatId(seatReq.getSeatId());
                seat.setPriceAtPurchase(seatPrice);
                seat.setBooking(booking);

                bookingSeats.add(seat);
                totalPrice += seatPrice;
            }

            booking.setBookingSeats(bookingSeats);

            // 7.2 Xử lý snack
            if (request.getSnacks() != null && !request.getSnacks().isEmpty()) {
                List<BookingSnack> bookingSnacks = new ArrayList<>();

                for (BookingRequestDTO.SnackRequest snackReq : request.getSnacks()) {
                    if (snackReq.getQuantity() == null || snackReq.getQuantity() <= 0) {
                        throw new RuntimeException("Số lượng snack không hợp lệ!");
                    }
                    if (snackReq.getQuantity() > MAX_SNACK_QUANTITY_PER_ITEM) {
                        throw new RuntimeException("Mỗi loại snack chỉ được đặt tối đa "
                                + MAX_SNACK_QUANTITY_PER_ITEM + " phần!");
                    }

                    Double snackPrice = getSnackPriceFromCatalog(snackReq.getSnackId());

                    BookingSnack snack = new BookingSnack();
                    snack.setSnackId(snackReq.getSnackId());
                    snack.setQuantity(snackReq.getQuantity());
                    snack.setPriceAtPurchase(snackPrice);
                    snack.setBooking(booking);

                    bookingSnacks.add(snack);
                    totalPrice += snackPrice * snackReq.getQuantity();
                }

                booking.setBookingSnacks(bookingSnacks);
            }

            booking.setTotalPrice(totalPrice);

            Booking savedBooking = bookingRepository.save(booking);

            return BookingResponseDTO.builder()
                    .bookingId(savedBooking.getId())
                    .status("PENDING")
                    .message("Giữ chỗ thành công! Vui lòng thanh toán trong 10 phút.")
                    .expiresInSeconds((int) SEAT_HOLD_TTL_SECONDS)
                    .totalPrice(savedBooking.getTotalPrice())
                    .build();

        } catch (RuntimeException ex) {
            for (String key : lockedKeys) {
                redisTemplate.delete(key);
            }

            throw ex;
        }
    }

    private ShowtimeResponseDTO getAndValidateShowtimeForBooking(String showtimeId) {
        String showtimeInfoUrl = "http://localhost:8080/api/v1/catalog/showtimes/" + showtimeId;
        ShowtimeResponseDTO showtimeInfo = restTemplate.getForObject(showtimeInfoUrl, ShowtimeResponseDTO.class);

        if (showtimeInfo == null) {
            throw new RuntimeException("Không lấy được thông tin suất chiếu từ Catalog!");
        }

        if (!"SCHEDULED".equalsIgnoreCase(showtimeInfo.getStatus())) {
            throw new RuntimeException("Suất chiếu này đã bị hủy hoặc không còn mở bán!");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = showtimeInfo.getStartTime();

        if (startTime == null) {
            throw new RuntimeException("Suất chiếu thiếu thông tin thời gian bắt đầu!");
        }

        if (!startTime.isAfter(now)) {
            throw new RuntimeException("Suất chiếu này đã diễn ra, không thể đặt vé!");
        }

        if (!startTime.isAfter(now.plusMinutes(BOOKING_CUTOFF_MINUTES))) {
            throw new RuntimeException("Suất chiếu sắp bắt đầu trong vòng 15 phút, hệ thống đã khóa bán vé!");
        }

        return showtimeInfo;
    }

    @Override
    public RoomSeatMatrixResponseDTO getSeatsForShowtime(String showtimeId) {
        ShowtimeResponseDTO showtimeInfo = getAndValidateShowtimeForBooking(showtimeId);

        if (showtimeInfo == null || showtimeInfo.getRoomId() == null) {
            throw new RuntimeException("Lấy thông tin suất chiếu từ Catalog thất bại!");
        }

        if (!"SCHEDULED".equalsIgnoreCase(showtimeInfo.getStatus())) {
            throw new RuntimeException("Suất chiếu này đã bị hủy hoặc không còn mở bán!");
        }

        String roomId = showtimeInfo.getRoomId();
        String roomName = showtimeInfo.getRoomName(); // Đã lấy được tên phòng từ đây!

        // ==========================================
        // 2. LẤY BẢN ĐỒ GHẾ GỐC TỪ CATALOG
        // ==========================================
        String catalogUrl = "http://localhost:8080/api/v1/catalog/rooms/internal/" + roomId + "/seats";
        SeatStatusResponseDTO[] rawSeatsArray = internalGet(catalogUrl, SeatStatusResponseDTO[].class);

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
            if ("MAINTENANCE".equalsIgnoreCase(seat.getSeatType())) {
                seat.setStatus("MAINTENANCE");
            } else if (paidSeatIds.contains(seat.getId())) {
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
        // Dùng PESSIMISTIC_WRITE để tránh 2 request cùng confirm 1 booking
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn này!"));

        // Kiểm tra chính chủ
        if (!booking.getUserId().equals(userId)) {
            throw new RuntimeException("Lỗi bảo mật: Bạn không có quyền thanh toán hóa đơn của người khác!");
        }

        // Chống thanh toán đúp
        if ("PAID".equalsIgnoreCase(booking.getStatus())) {
            throw new RuntimeException("Hóa đơn này đã được thanh toán trước đó, không thể thanh toán lại!");
        }

        // Chặn thanh toán booking đã hủy
        if ("CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            throw new RuntimeException("Hóa đơn này đã bị hủy hoặc đã hết hạn thanh toán!");
        }

        // Chỉ cho phép thanh toán khi booking còn PENDING
        if (!"PENDING".equalsIgnoreCase(booking.getStatus())) {
            throw new RuntimeException("Hóa đơn này không ở trạng thái chờ thanh toán!");
        }

        // Nếu bạn đã vá nhóm 2, giữ đoạn check hết hạn 10 phút này
        LocalDateTime expiredAt = booking.getBookingTime().plusSeconds(SEAT_HOLD_TTL_SECONDS);

        if (LocalDateTime.now().isAfter(expiredAt)) {
            booking.setStatus("CANCELLED");
            bookingRepository.save(booking);

            String redisKeyPrefix = "lock:showtime:" + booking.getShowtimeId() + ":seat:";
            for (BookingSeat seat : booking.getBookingSeats()) {
                redisTemplate.delete(redisKeyPrefix + seat.getSeatId());
            }

            throw new RuntimeException("Hóa đơn đã quá thời gian thanh toán 10 phút!");
        }

        // Nếu bạn đã vá nhóm 2, giữ đoạn check suất chiếu này
        getAndValidateShowtimeForBooking(booking.getShowtimeId());

        // Chốt đơn
        booking.setStatus("PAID");
        Booking savedBooking = bookingRepository.save(booking);

        // Xóa Redis lock
        String redisKeyPrefix = "lock:showtime:" + savedBooking.getShowtimeId() + ":seat:";

        for (BookingSeat seat : savedBooking.getBookingSeats()) {
            redisTemplate.delete(redisKeyPrefix + seat.getSeatId());
        }

        return BookingResponseDTO.builder()
                .bookingId(savedBooking.getId())
                .status("PAID")
                .message("Thanh toán thành công! Vé của bạn đã được xác nhận.")
                .totalPrice(savedBooking.getTotalPrice())
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

        long elapsedSeconds = Duration.between(booking.getBookingTime(), LocalDateTime.now()).getSeconds();
        long remainingSeconds = SEAT_HOLD_TTL_SECONDS - elapsedSeconds;

        if (remainingSeconds < 0 || "CANCELLED".equalsIgnoreCase(booking.getStatus())) {
            remainingSeconds = 0;
        }

        List<BookingResponseDTO.SeatItem> seatItems = buildBookingSeatItems(booking);
        List<BookingResponseDTO.SnackItem> snackItems = buildBookingSnackItems(booking);

        return BookingResponseDTO.builder()
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .expiresInSeconds(remainingSeconds)
                .message("Lấy thông tin hóa đơn thành công.")
                .seats(seatItems)
                .snacks(snackItems)
                .build();
    }

    @Override
    public Double getSeatPriceFromCatalog(String showtimeId, String seatId) {
        String url = "http://localhost:8080/api/v1/catalog/showtimes/" + showtimeId + "/seats/" + seatId + "/price";

        Double price = internalGet(url, Double.class);

        if (price == null) {
            throw new RuntimeException("Không lấy được giá ghế từ Catalog!");
        }

        return price;
    }

    @Override
    public boolean hasActiveBookingForShowtimes(List<String> showtimeIds) {
        if (showtimeIds == null || showtimeIds.isEmpty()) {
            return false;
        }

        List<String> activeStatuses = List.of("PENDING", "PAID");

        return bookingRepository.existsActiveBookingByShowtimeIds(showtimeIds, activeStatuses);
    }

    @Override
    public Double getSnackPriceFromCatalog(String snackId) {
        String url = "http://localhost:8080/api/v1/catalog/snacks/" + snackId + "/price";
        Double price = internalGet(url, Double.class);

        if (price == null) {
            throw new RuntimeException("Không lấy được giá bắp nước từ Catalog!");
        }

        return price;
    }

    private <T> T internalGet(String url, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<T> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                responseType
        );

        return response.getBody();
    }

    private void validateSeatSelectionRules(String showtimeId, String roomId, List<String> selectedSeatIds) {
        if (roomId == null || roomId.isBlank()) {
            throw new RuntimeException("Suất chiếu thiếu thông tin phòng chiếu!");
        }

        if (selectedSeatIds == null || selectedSeatIds.isEmpty()) {
            throw new RuntimeException("Vui lòng chọn ít nhất 1 ghế!");
        }

        List<SeatStatusResponseDTO> allSeats = loadSeatsWithCurrentStatus(showtimeId, roomId);

        Map<String, SeatStatusResponseDTO> seatMap = allSeats.stream()
                .collect(Collectors.toMap(
                        SeatStatusResponseDTO::getId,
                        seat -> seat,
                        (oldValue, newValue) -> oldValue
                ));

        Set<String> selectedSet = new HashSet<>(selectedSeatIds);

        int logicalSeatCount = 0;

        for (String seatId : selectedSeatIds) {
            if (seatId == null || seatId.isBlank()) {
                throw new RuntimeException("ID ghế không hợp lệ!");
            }

            SeatStatusResponseDTO seat = seatMap.get(seatId);

            if (seat == null) {
                throw new RuntimeException("Ghế " + seatId + " không thuộc phòng chiếu của suất chiếu này!");
            }

            if ("PAID".equalsIgnoreCase(seat.getStatus())) {
                throw new RuntimeException("Ghế " + buildSeatName(seat) + " đã được thanh toán!");
            }

            if ("LOCKED".equalsIgnoreCase(seat.getStatus())) {
                throw new RuntimeException("Ghế " + buildSeatName(seat) + " đang được người khác giữ!");
            }

            if ("MAINTENANCE".equalsIgnoreCase(seat.getStatus())
                    || "MAINTENANCE".equalsIgnoreCase(seat.getSeatType())) {
                throw new RuntimeException("Ghế " + buildSeatName(seat) + " đang bảo trì, không thể đặt vé!");
            }

            // Rule COUPLE:
            // 1 ghế COUPLE là 1 seatId vật lý nhưng tính là 2 chỗ ngồi.
            if ("COUPLE".equalsIgnoreCase(seat.getSeatType())) {
                logicalSeatCount += 2;
            } else {
                logicalSeatCount += 1;
            }
        }

        if (logicalSeatCount > MAX_SEATS_PER_BOOKING) {
            throw new RuntimeException("Mỗi đơn chỉ được mua tối đa "
                    + MAX_SEATS_PER_BOOKING
                    + " chỗ ngồi. Ghế COUPLE được tính là 2 chỗ.");
        }

        validateNoSingleOrphanSeat(allSeats, selectedSet);
    }

    private List<SeatStatusResponseDTO> loadSeatsWithCurrentStatus(String showtimeId, String roomId) {
        String catalogUrl = "http://localhost:8080/api/v1/catalog/rooms/internal/" + roomId + "/seats";
        SeatStatusResponseDTO[] rawSeatsArray = internalGet(catalogUrl, SeatStatusResponseDTO[].class);

        if (rawSeatsArray == null || rawSeatsArray.length == 0) {
            throw new RuntimeException("Phòng chiếu chưa có sơ đồ ghế!");
        }

        List<SeatStatusResponseDTO> seats = new ArrayList<>(Arrays.asList(rawSeatsArray));

        List<String> paidSeatIds = bookingRepository.findPaidSeatIdsByShowtime(showtimeId);

        String redisKeyPrefix = "lock:showtime:" + showtimeId + ":seat:";
        Set<String> lockedSeatKeys = redisTemplate.keys(redisKeyPrefix + "*");

        for (SeatStatusResponseDTO seat : seats) {
            String seatKey = redisKeyPrefix + seat.getId();

            if ("MAINTENANCE".equalsIgnoreCase(seat.getSeatType())) {
                seat.setStatus("MAINTENANCE");
            } else if (paidSeatIds.contains(seat.getId())) {
                seat.setStatus("PAID");
            } else if (lockedSeatKeys != null && lockedSeatKeys.contains(seatKey)) {
                seat.setStatus("LOCKED");
            } else {
                seat.setStatus("AVAILABLE");
            }
        }

        return seats;
    }

    private void validateNoSingleOrphanSeat(List<SeatStatusResponseDTO> allSeats, Set<String> selectedSeatIds) {
        Map<Integer, List<SeatStatusResponseDTO>> groupedByRow = allSeats.stream()
                .collect(Collectors.groupingBy(
                        SeatStatusResponseDTO::getGridRow,
                        TreeMap::new,
                        Collectors.toList()
                ));

        for (List<SeatStatusResponseDTO> rowSeats : groupedByRow.values()) {
            rowSeats.sort(Comparator.comparingInt(SeatStatusResponseDTO::getGridColumn));

            List<List<SeatStatusResponseDTO>> continuousBlocks = splitByContinuousColumns(rowSeats);

            for (List<SeatStatusResponseDTO> block : continuousBlocks) {
                if (block.size() < 3) {
                    continue;
                }

                for (int i = 1; i < block.size() - 1; i++) {
                    SeatStatusResponseDTO current = block.get(i);
                    SeatStatusResponseDTO left = block.get(i - 1);
                    SeatStatusResponseDTO right = block.get(i + 1);

                    boolean currentAvailableAfterSelection = isAvailableAfterSelection(current, selectedSeatIds);
                    boolean leftUnavailableAfterSelection = isUnavailableAfterSelection(left, selectedSeatIds);
                    boolean rightUnavailableAfterSelection = isUnavailableAfterSelection(right, selectedSeatIds);

                    // Chặn case kiểu:
                    // [Đã bán/đang chọn] [Trống lẻ] [Đã bán/đang chọn]
                    if (currentAvailableAfterSelection
                            && leftUnavailableAfterSelection
                            && rightUnavailableAfterSelection) {
                        throw new RuntimeException("Không thể chọn ghế vì sẽ để lại ghế trống lẻ cô lập: "
                                + buildSeatName(current));
                    }
                }
            }
        }
    }

    private List<List<SeatStatusResponseDTO>> splitByContinuousColumns(List<SeatStatusResponseDTO> rowSeats) {
        List<List<SeatStatusResponseDTO>> blocks = new ArrayList<>();

        if (rowSeats == null || rowSeats.isEmpty()) {
            return blocks;
        }

        List<SeatStatusResponseDTO> currentBlock = new ArrayList<>();
        currentBlock.add(rowSeats.get(0));

        for (int i = 1; i < rowSeats.size(); i++) {
            SeatStatusResponseDTO previous = rowSeats.get(i - 1);
            SeatStatusResponseDTO current = rowSeats.get(i);

            // Nếu column không liền nhau, xem như có lối đi ở giữa.
            // Không kiểm tra ghế lẻ xuyên qua lối đi.
            if (current.getGridColumn() == previous.getGridColumn() + 1) {
                currentBlock.add(current);
            } else {
                blocks.add(currentBlock);
                currentBlock = new ArrayList<>();
                currentBlock.add(current);
            }
        }

        blocks.add(currentBlock);
        return blocks;
    }

    private boolean isAvailableAfterSelection(SeatStatusResponseDTO seat, Set<String> selectedSeatIds) {
        return !isUnavailableAfterSelection(seat, selectedSeatIds);
    }

    private boolean isUnavailableAfterSelection(SeatStatusResponseDTO seat, Set<String> selectedSeatIds) {
        if (seat == null) {
            return true;
        }

        if (selectedSeatIds.contains(seat.getId())) {
            return true;
        }

        if ("PAID".equalsIgnoreCase(seat.getStatus())) {
            return true;
        }

        if ("LOCKED".equalsIgnoreCase(seat.getStatus())) {
            return true;
        }

        if ("MAINTENANCE".equalsIgnoreCase(seat.getStatus())
                || "MAINTENANCE".equalsIgnoreCase(seat.getSeatType())) {
            return true;
        }

        return false;
    }

    private String buildSeatName(SeatStatusResponseDTO seat) {
        if (seat == null) {
            return "";
        }

        String row = seat.getRowName() == null ? "" : seat.getRowName();
        String label = seat.getSeatLabel() == null ? "" : seat.getSeatLabel();

        return row + label;
    }

    private List<BookingResponseDTO.SeatItem> buildBookingSeatItems(Booking booking) {
        Map<String, String> seatNameMap = getSeatNameMapByShowtimeId(booking.getShowtimeId());

        List<BookingResponseDTO.SeatItem> result = new ArrayList<>();

        if (booking.getBookingSeats() == null || booking.getBookingSeats().isEmpty()) {
            return result;
        }

        for (BookingSeat bookingSeat : booking.getBookingSeats()) {
            String seatId = bookingSeat.getSeatId();
            String seatName = seatNameMap.getOrDefault(seatId, seatId);

            result.add(
                    BookingResponseDTO.SeatItem.builder()
                            .seatId(seatId)
                            .seatName(seatName)
                            .price(toLongMoney(bookingSeat.getPriceAtPurchase()))
                            .build()
            );
        }

        return result;
    }

    private List<BookingResponseDTO.SnackItem> buildBookingSnackItems(Booking booking) {
        List<BookingResponseDTO.SnackItem> result = new ArrayList<>();

        if (booking.getBookingSnacks() == null || booking.getBookingSnacks().isEmpty()) {
            return result;
        }

        for (BookingSnack bookingSnack : booking.getBookingSnacks()) {
            String snackId = bookingSnack.getSnackId();
            String snackName = getSnackNameFromCatalog(snackId);

            result.add(
                    BookingResponseDTO.SnackItem.builder()
                            .snackId(snackId)
                            .snackName(snackName)
                            .quantity(bookingSnack.getQuantity())
                            .price(toLongMoney(bookingSnack.getPriceAtPurchase()))
                            .build()
            );
        }

        return result;
    }

    private Map<String, String> getSeatNameMapByShowtimeId(String showtimeId) {
        ShowtimeResponseDTO showtimeInfo = getShowtimeInfoOnly(showtimeId);

        if (showtimeInfo == null || showtimeInfo.getRoomId() == null) {
            return new HashMap<>();
        }

        String url = catalogServiceUrl + "/rooms/internal/" + showtimeInfo.getRoomId() + "/seats";

        SeatStatusResponseDTO[] seats = internalGet(url, SeatStatusResponseDTO[].class);

        Map<String, String> result = new HashMap<>();

        if (seats == null || seats.length == 0) {
            return result;
        }

        for (SeatStatusResponseDTO seat : seats) {
            String seatName = buildSeatName(seat);
            result.put(seat.getId(), seatName);
        }

        return result;
    }

    private ShowtimeResponseDTO getShowtimeInfoOnly(String showtimeId) {
        String url = catalogServiceUrl + "/showtimes/" + showtimeId;

        ShowtimeResponseDTO showtimeInfo = restTemplate.getForObject(url, ShowtimeResponseDTO.class);

        if (showtimeInfo == null) {
            throw new RuntimeException("Không lấy được thông tin suất chiếu từ Catalog!");
        }

        return showtimeInfo;
    }

    private String getSnackNameFromCatalog(String snackId) {
        try {
            String url = catalogServiceUrl + "/snacks/" + snackId;

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    Map.class
            );

            Map<String, Object> body = response.getBody();

            if (body == null || body.get("name") == null) {
                return snackId;
            }

            return body.get("name").toString();

        } catch (Exception e) {
            return snackId;
        }
    }

    private Long toLongMoney(Double value) {
        if (value == null) {
            return 0L;
        }

        return Math.round(value);
    }
}
