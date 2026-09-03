package com.movie.booking_service.service;

import com.movie.booking_service.dto.BookingResponseDTO;
import com.movie.booking_service.dto.BookingSummaryDTO;
import com.movie.booking_service.dto.ShowtimeResponseDTO;
import com.movie.booking_service.dto.TicketResponseDTO;
import com.movie.booking_service.entity.Booking;
import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.Ticket;
import com.movie.booking_service.repository.BookingRepository;
import com.movie.booking_service.repository.BookingSeatRepository;
import com.movie.booking_service.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";
    private static final String BOOKING_ID = "booking-1";
    private static final String SHOWTIME_ID = "showtime-1";
    private static final String CATALOG_URL = "http://localhost:8081/api/v1/catalog";
    private static final String PAYMENT_URL = "http://localhost:8084/api/v1/payment";

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private BookingSeatRepository bookingSeatRepository;

    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl();
        ReflectionTestUtils.setField(service, "bookingRepository", bookingRepository);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "modelMapper", modelMapper);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "ticketRepository", ticketRepository);
        ReflectionTestUtils.setField(service, "bookingSeatRepository", bookingSeatRepository);
        ReflectionTestUtils.setField(service, "internalSecret", "secret");
        ReflectionTestUtils.setField(service, "catalogServiceUrl", CATALOG_URL);
        ReflectionTestUtils.setField(service, "paymentServiceUrl", PAYMENT_URL);
        ReflectionTestUtils.setField(service, "notificationServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(service, "reminderBeforeMinutes", 60);
    }

    private Booking booking(String status, LocalDateTime bookingTime) {
        Booking b = new Booking();
        b.setId(BOOKING_ID);
        b.setUserId(USER_ID);
        b.setShowtimeId(SHOWTIME_ID);
        b.setStatus(status);
        b.setTotalPrice(200_000.0);
        b.setBookingTime(bookingTime);

        BookingSeat seat = new BookingSeat();
        seat.setId("bs-1");
        seat.setSeatId("seat-A1");
        seat.setPriceAtPurchase(100_000.0);
        seat.setBooking(b);
        b.setBookingSeats(List.of(seat));
        return b;
    }

    private void stubFutureShowtime() {
        ShowtimeResponseDTO showtime = new ShowtimeResponseDTO();
        showtime.setStatus("SCHEDULED");
        showtime.setStartTime(LocalDateTime.now().plusHours(3));
        lenient().when(restTemplate.exchange(
                        contains("/showtimes/"), eq(HttpMethod.GET), any(HttpEntity.class), eq(ShowtimeResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(showtime, HttpStatus.OK));
    }

    // ----------------------------------------------------------------
    // confirmPayment
    // ----------------------------------------------------------------
    @Nested
    class ConfirmPayment {

        @Test
        void bookingNotFound_throws() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.confirmPayment(USER_ID, BOOKING_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Không tìm thấy hóa đơn");
        }

        @Test
        void wrongUser_throws() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking("PENDING", LocalDateTime.now())));

            assertThatThrownBy(() -> service.confirmPayment(OTHER_USER_ID, BOOKING_ID))
                    .hasMessageContaining("không có quyền");

            verify(bookingRepository, never()).save(any());
        }

        @Test
        void alreadyPaid_isIdempotentAndHasNoSideEffects() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking("PAID", LocalDateTime.now())));

            BookingResponseDTO result = service.confirmPayment(USER_ID, BOOKING_ID);

            assertThat(result.getStatus()).isEqualTo("PAID");
            verify(bookingRepository, never()).save(any());
            verify(ticketRepository, never()).saveAll(anyList());
        }

        @Test
        void cancelledBooking_throws() {
            when(bookingRepository.findByIdForUpdate(BOOKING_ID))
                    .thenReturn(Optional.of(booking("CANCELLED", LocalDateTime.now())));

            assertThatThrownBy(() -> service.confirmPayment(USER_ID, BOOKING_ID))
                    .hasMessageContaining("đã bị hủy");
        }

        @Test
        void expiredHold_cancelsBookingAndThrows() {
            Booking expired = booking("PENDING", LocalDateTime.now().minusMinutes(11));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.confirmPayment(USER_ID, BOOKING_ID))
                    .hasMessageContaining("quá thời gian thanh toán");

            assertThat(expired.getStatus()).isEqualTo("CANCELLED");
            verify(bookingRepository).save(expired);
            verify(redisTemplate).delete("lock:showtime:" + SHOWTIME_ID + ":seat:seat-A1");
        }

        @Test
        void success_marksPaidAndGeneratesOneTicketPerSeat() {
            Booking pending = booking("PENDING", LocalDateTime.now().minusMinutes(2));
            when(bookingRepository.findByIdForUpdate(BOOKING_ID)).thenReturn(Optional.of(pending));
            when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
            stubFutureShowtime();

            BookingResponseDTO result = service.confirmPayment(USER_ID, BOOKING_ID);

            assertThat(result.getStatus()).isEqualTo("PAID");
            assertThat(pending.getStatus()).isEqualTo("PAID");

            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<Ticket>> captor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(ticketRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).getBookingSeatId()).isEqualTo("bs-1");

            verify(redisTemplate).delete("lock:showtime:" + SHOWTIME_ID + ":seat:seat-A1");
        }
    }

    // ----------------------------------------------------------------
    // cancelBooking
    // ----------------------------------------------------------------
    @Nested
    class CancelBooking {

        @Test
        void notFound_throws() {
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelBooking(USER_ID, BOOKING_ID, "đổi lịch"))
                    .hasMessageContaining("Không tìm thấy");
        }

        @Test
        void wrongUser_throws() {
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(booking("PENDING", LocalDateTime.now())));

            assertThatThrownBy(() -> service.cancelBooking(OTHER_USER_ID, BOOKING_ID, null))
                    .hasMessageContaining("không có quyền");
        }

        @Test
        void alreadyCancelled_throws() {
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(booking("CANCELLED", LocalDateTime.now())));

            assertThatThrownBy(() -> service.cancelBooking(USER_ID, BOOKING_ID, null))
                    .hasMessageContaining("đã được hủy trước đó");
        }

        @Test
        void pendingBooking_cancelsWithoutRefund() {
            Booking pending = booking("PENDING", LocalDateTime.now());
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(pending));

            BookingResponseDTO result = service.cancelBooking(USER_ID, BOOKING_ID, "đổi ý");

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(pending.getStatus()).isEqualTo("CANCELLED");
            assertThat(pending.getRefundStatus()).isEqualTo("NOT_APPLICABLE");
            assertThat(pending.getCancellationReason()).isEqualTo("đổi ý");
            // Không gọi payment-service khi vé chưa thanh toán
            verify(restTemplate, never()).exchange(
                    anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class));
        }

        @Test
        void paidBooking_showtimeAlreadyStarted_throws() {
            Booking paid = booking("PAID", LocalDateTime.now().minusDays(1));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(paid));

            ShowtimeResponseDTO started = new ShowtimeResponseDTO();
            started.setStartTime(LocalDateTime.now().minusMinutes(30));
            when(restTemplate.exchange(contains("/showtimes/"), eq(HttpMethod.GET),
                    any(HttpEntity.class), eq(ShowtimeResponseDTO.class)))
                    .thenReturn(new ResponseEntity<>(started, HttpStatus.OK));

            assertThatThrownBy(() -> service.cancelBooking(USER_ID, BOOKING_ID, null))
                    .hasMessageContaining("đã bắt đầu");
            assertThat(paid.getStatus()).isEqualTo("PAID");
        }

        @Test
        void paidBooking_futureShowtime_autoRefundSuccess_marksCompleted() {
            Booking paid = booking("PAID", LocalDateTime.now().minusDays(1));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(paid));
            stubFutureShowtime();
            when(restTemplate.exchange(contains("/momo/refund/"), eq(HttpMethod.POST),
                    any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("status", "SUCCESS"), HttpStatus.OK));

            BookingResponseDTO result = service.cancelBooking(USER_ID, BOOKING_ID, "rạp hỏng máy chiếu");

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(paid.getRefundStatus()).isEqualTo("COMPLETED");
        }

        @Test
        void paidBooking_refundRejected_marksFailed() {
            Booking paid = booking("PAID", LocalDateTime.now().minusDays(1));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(paid));
            stubFutureShowtime();
            when(restTemplate.exchange(contains("/momo/refund/"), eq(HttpMethod.POST),
                    any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("status", "FAILED"), HttpStatus.OK));

            service.cancelBooking(USER_ID, BOOKING_ID, null);

            assertThat(paid.getStatus()).isEqualTo("CANCELLED");
            assertThat(paid.getRefundStatus()).isEqualTo("FAILED");
        }

        @Test
        void paidBooking_paymentServiceUnreachable_keepsRefundPending() {
            Booking paid = booking("PAID", LocalDateTime.now().minusDays(1));
            when(bookingRepository.findById(BOOKING_ID)).thenReturn(Optional.of(paid));
            stubFutureShowtime();
            when(restTemplate.exchange(contains("/momo/refund/"), eq(HttpMethod.POST),
                    any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("connection refused"));

            BookingResponseDTO result = service.cancelBooking(USER_ID, BOOKING_ID, null);

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            assertThat(paid.getRefundStatus()).isEqualTo("PENDING");
        }
    }

    // ----------------------------------------------------------------
    // getMyBookings
    // ----------------------------------------------------------------
    @Nested
    class GetMyBookings {

        @Test
        void pendingWithinHold_hasPositiveRemaining_paidHasZero() {
            Booking pending = booking("PENDING", LocalDateTime.now().minusMinutes(2));
            pending.setId("b-pending");
            Booking paid = booking("PAID", LocalDateTime.now().minusDays(1));
            paid.setId("b-paid");
            when(bookingRepository.findByUserIdOrderByBookingTimeDesc(USER_ID))
                    .thenReturn(List.of(pending, paid));

            List<BookingSummaryDTO> result = service.getMyBookings(USER_ID);

            assertThat(result).hasSize(2);
            BookingSummaryDTO pendingDto = result.stream()
                    .filter(d -> d.getBookingId().equals("b-pending")).findFirst().orElseThrow();
            BookingSummaryDTO paidDto = result.stream()
                    .filter(d -> d.getBookingId().equals("b-paid")).findFirst().orElseThrow();

            assertThat(pendingDto.getExpiresInSeconds()).isBetween(1L, 600L);
            assertThat(paidDto.getExpiresInSeconds()).isZero();
        }

        @Test
        void pendingButHoldExpired_hasZeroRemaining() {
            Booking pending = booking("PENDING", LocalDateTime.now().minusMinutes(20));
            when(bookingRepository.findByUserIdOrderByBookingTimeDesc(USER_ID))
                    .thenReturn(List.of(pending));

            List<BookingSummaryDTO> result = service.getMyBookings(USER_ID);

            assertThat(result.get(0).getExpiresInSeconds()).isZero();
        }
    }

    // ----------------------------------------------------------------
    // getTickets
    // ----------------------------------------------------------------
    @Nested
    class GetTickets {

        @Test
        void wrongUser_throws() {
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(booking("PAID", LocalDateTime.now())));

            assertThatThrownBy(() -> service.getTickets(OTHER_USER_ID, BOOKING_ID))
                    .hasMessageContaining("Không có quyền");
        }

        @Test
        void notPaid_throws() {
            when(bookingRepository.findById(BOOKING_ID))
                    .thenReturn(Optional.of(booking("PENDING", LocalDateTime.now())));

            assertThatThrownBy(() -> service.getTickets(USER_ID, BOOKING_ID))
                    .hasMessageContaining("chưa thanh toán");
        }
    }

    // ----------------------------------------------------------------
    // checkInTicket
    // ----------------------------------------------------------------
    @Nested
    class CheckInTicket {

        @Test
        void invalidQrCode_throws() {
            when(ticketRepository.findByQrCode("bad-qr")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.checkInTicket("staff-1", "bad-qr"))
                    .hasMessageContaining("Mã QR không hợp lệ");
        }

        @Test
        void alreadyCheckedIn_throws() {
            Ticket ticket = new Ticket();
            ticket.setId("t-1");
            ticket.setBookingSeatId("bs-1");
            ticket.setCheckedInAt(LocalDateTime.now().minusHours(1));
            when(ticketRepository.findByQrCode("qr-1")).thenReturn(Optional.of(ticket));

            assertThatThrownBy(() -> service.checkInTicket("staff-1", "qr-1"))
                    .hasMessageContaining("đã được soát vé");
        }

        @Test
        void bookingNotPaid_throws() {
            Ticket ticket = new Ticket();
            ticket.setId("t-1");
            ticket.setBookingSeatId("bs-1");
            when(ticketRepository.findByQrCode("qr-1")).thenReturn(Optional.of(ticket));

            Booking cancelled = booking("CANCELLED", LocalDateTime.now());
            BookingSeat seat = cancelled.getBookingSeats().get(0);
            when(bookingSeatRepository.findById("bs-1")).thenReturn(Optional.of(seat));

            assertThatThrownBy(() -> service.checkInTicket("staff-1", "qr-1"))
                    .hasMessageContaining("đã bị hủy");
            verify(ticketRepository, never()).save(any());
        }
    }
}
