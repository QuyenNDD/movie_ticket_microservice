package com.movie.payment_service.service;

import com.movie.payment_service.dto.BookingConfirmResultEvent;
import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.dto.RefundResponseDTO;
import com.movie.payment_service.entity.PaymentStatus;
import com.movie.payment_service.entity.PaymentTransaction;
import com.movie.payment_service.entity.RefundStatus;
import com.movie.payment_service.entity.RefundTransaction;
import com.movie.payment_service.publisher.BookingConfirmRequestPublisher;
import com.movie.payment_service.publisher.BookingPaidEmailPublisher;
import com.movie.payment_service.repository.PaymentTransactionRepository;
import com.movie.payment_service.repository.RefundTransactionRepository;
import com.movie.payment_service.util.HmacSHA256Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MomoServiceImplTest {

    private static final String BOOKING_ID = "booking-1";
    private static final String PAYMENT_ID = "pay-1";
    private static final String REFUND_ENDPOINT = "https://momo.test/refund";

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private RefundTransactionRepository refundTransactionRepository;
    @Mock
    private BookingPaidEmailPublisher bookingPaidEmailPublisher;
    @Mock
    private BookingConfirmRequestPublisher bookingConfirmRequestPublisher;
    @Mock
    private RestTemplate restTemplate;

    private MomoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MomoServiceImpl();
        ReflectionTestUtils.setField(service, "paymentTransactionRepository", paymentTransactionRepository);
        ReflectionTestUtils.setField(service, "refundTransactionRepository", refundTransactionRepository);
        ReflectionTestUtils.setField(service, "bookingPaidEmailPublisher", bookingPaidEmailPublisher);
        ReflectionTestUtils.setField(service, "bookingConfirmRequestPublisher", bookingConfirmRequestPublisher);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "accessKey", "access-key");
        ReflectionTestUtils.setField(service, "secretKey", "secret-key");
        ReflectionTestUtils.setField(service, "partnerCode", "PARTNER");
        ReflectionTestUtils.setField(service, "refundEndpoint", REFUND_ENDPOINT);
        ReflectionTestUtils.setField(service, "internalSecret", "internal");
        ReflectionTestUtils.setField(service, "authServiceUrl", "http://localhost:8083/api/v1/auth");
        ReflectionTestUtils.setField(service, "bookingServiceUrl", "http://localhost:8082/api/v1/booking");
        ReflectionTestUtils.setField(service, "maxRetryCount", 10);
    }

    private PaymentTransaction payment(PaymentStatus status, String transId) {
        PaymentTransaction p = new PaymentTransaction();
        p.setId(PAYMENT_ID);
        p.setBookingId(BOOKING_ID);
        p.setUserId("user-1");
        p.setOrderId("order-1");
        p.setAmount(200_000L);
        p.setStatus(status);
        p.setTransId(transId);
        return p;
    }

    // ----------------------------------------------------------------
    // refundPayment
    // ----------------------------------------------------------------
    @Nested
    class RefundPayment {

        @Test
        void paymentNotFound_throws() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refundPayment(BOOKING_ID, "hủy vé"))
                    .hasMessageContaining("Không tìm thấy giao dịch");
        }

        @Test
        void paymentNotSuccess_throws() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.INIT, "123456")));

            assertThatThrownBy(() -> service.refundPayment(BOOKING_ID, null))
                    .hasMessageContaining("chưa thanh toán thành công");
        }

        @Test
        void alreadyRefunded_isIdempotent_noMomoCall() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "123456")));

            RefundTransaction done = new RefundTransaction();
            done.setId("refund-1");
            done.setStatus(RefundStatus.SUCCESS);
            done.setAmount(200_000L);
            when(refundTransactionRepository.findByPaymentTransactionId(PAYMENT_ID))
                    .thenReturn(Optional.of(done));

            RefundResponseDTO result = service.refundPayment(BOOKING_ID, null);

            assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCESS);
            verify(restTemplate, never()).postForObject(any(String.class), any(), eq(Map.class));
            verify(refundTransactionRepository, never()).save(any());
        }

        @Test
        void nonNumericTransId_marksFailedWithoutCallingMomo() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "TEST_TRANS_123")));
            when(refundTransactionRepository.findByPaymentTransactionId(PAYMENT_ID))
                    .thenReturn(Optional.empty());

            RefundResponseDTO result = service.refundPayment(BOOKING_ID, "hủy vé test");

            assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
            assertThat(result.getMessage()).contains("giả lập");
            verify(restTemplate, never()).postForObject(any(String.class), any(), eq(Map.class));
        }

        @Test
        void momoAcceptsRefund_marksSuccessAndStoresMomoTransId() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "123456")));
            when(refundTransactionRepository.findByPaymentTransactionId(PAYMENT_ID))
                    .thenReturn(Optional.empty());
            when(restTemplate.postForObject(eq(REFUND_ENDPOINT), any(), eq(Map.class)))
                    .thenReturn(Map.of("resultCode", 0, "transId", 987654321L));

            RefundResponseDTO result = service.refundPayment(BOOKING_ID, "rạp hủy suất");

            assertThat(result.getStatus()).isEqualTo(RefundStatus.SUCCESS);

            org.mockito.ArgumentCaptor<RefundTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(RefundTransaction.class);
            verify(refundTransactionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
            RefundTransaction saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(RefundStatus.SUCCESS);
            assertThat(saved.getMomoRefundTransId()).isEqualTo("987654321");
            assertThat(saved.getRefundedAt()).isNotNull();
        }

        @Test
        void momoRejectsRefund_marksFailed() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "123456")));
            when(refundTransactionRepository.findByPaymentTransactionId(PAYMENT_ID))
                    .thenReturn(Optional.empty());
            when(restTemplate.postForObject(eq(REFUND_ENDPOINT), any(), eq(Map.class)))
                    .thenReturn(Map.of("resultCode", 42, "message", "invalid transId"));

            RefundResponseDTO result = service.refundPayment(BOOKING_ID, null);

            assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        }

        @Test
        void momoCallThrows_marksFailed() {
            when(paymentTransactionRepository.findByBookingId(BOOKING_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "123456")));
            when(refundTransactionRepository.findByPaymentTransactionId(PAYMENT_ID))
                    .thenReturn(Optional.empty());
            when(restTemplate.postForObject(eq(REFUND_ENDPOINT), any(), eq(Map.class)))
                    .thenThrow(new RestClientException("timeout"));

            RefundResponseDTO result = service.refundPayment(BOOKING_ID, null);

            assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
        }
    }

    // ----------------------------------------------------------------
    // handleBookingConfirmResult
    // ----------------------------------------------------------------
    @Nested
    class HandleBookingConfirmResult {

        private BookingConfirmResultEvent event(boolean success, String error) {
            return BookingConfirmResultEvent.builder()
                    .paymentId(PAYMENT_ID)
                    .bookingId(BOOKING_ID)
                    .success(success)
                    .errorMessage(error)
                    .build();
        }

        @Test
        void paymentNotFound_returnsQuietly() {
            when(paymentTransactionRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

            service.handleBookingConfirmResult(event(true, null));

            verify(paymentTransactionRepository, never()).save(any());
        }

        @Test
        void alreadySuccess_isNoOp() {
            when(paymentTransactionRepository.findById(PAYMENT_ID))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "123456")));

            service.handleBookingConfirmResult(event(true, null));

            verify(paymentTransactionRepository, never()).save(any());
            verify(bookingPaidEmailPublisher, never()).publish(any());
        }

        @Test
        void confirmFailed_setsLastErrorAndKeepsStatus() {
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, "123456");
            when(paymentTransactionRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(p));

            service.handleBookingConfirmResult(event(false, "ghế đã bị người khác thanh toán"));

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING);
            assertThat(p.getLastError()).isEqualTo("ghế đã bị người khác thanh toán");
            verify(bookingPaidEmailPublisher, never()).publish(any());
        }

        @Test
        void confirmSucceeded_marksSuccessAndSetsPaidAt() {
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, "123456");
            p.setPaidAt(null);
            when(paymentTransactionRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(p));
            lenient().when(paymentTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.handleBookingConfirmResult(event(true, null));

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(p.getPaidAt()).isNotNull();
            assertThat(p.getLastError()).isNull();
        }
    }

    // ----------------------------------------------------------------
    // confirmBookingAndMarkSuccess (retry state machine)
    // ----------------------------------------------------------------
    @Nested
    class ConfirmBookingAndMarkSuccess {

        @Test
        void underRetryLimit_publishesConfirmRequest() {
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, "123456");
            p.setRetryCount(0);

            service.confirmBookingAndMarkSuccess(p);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING);
            assertThat(p.getRetryCount()).isEqualTo(1);
            assertThat(p.getNextRetryAt()).isNotNull();
            verify(bookingConfirmRequestPublisher).publish(any());
        }

        @Test
        void atRetryLimit_marksPaymentReviewWithoutPublishing() {
            ReflectionTestUtils.setField(service, "maxRetryCount", 3);
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, "123456");
            p.setRetryCount(2);

            service.confirmBookingAndMarkSuccess(p);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAYMENT_REVIEW);
            assertThat(p.getNextRetryAt()).isNull();
            verify(bookingConfirmRequestPublisher, never()).publish(any());
        }

        @Test
        void publishThrows_recordsLastError() {
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, "123456");
            p.setRetryCount(0);
            org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                    .when(bookingConfirmRequestPublisher).publish(any());

            service.confirmBookingAndMarkSuccess(p);

            assertThat(p.getLastError()).contains("broker down");
        }
    }

    // ----------------------------------------------------------------
    // processIpn (xác thực chữ ký + máy trạng thái IPN)
    // ----------------------------------------------------------------
    @Nested
    class ProcessIpn {

        private static final String USER_ID = "user-1";

        /** Dựng IPN có chữ ký hợp lệ đúng theo thuật toán trong validateMomoSignature. */
        private MoMoIpnDTO signedIpn(String orderId, String amount, int resultCode, String transId) {
            MoMoIpnDTO dto = new MoMoIpnDTO();
            dto.setPartnerCode("PARTNER");
            dto.setOrderId(orderId);
            dto.setRequestId(orderId);
            dto.setAmount(amount);
            dto.setOrderInfo("Thanh toan ve phim");
            dto.setOrderType("momo_wallet");
            dto.setTransId(transId);
            dto.setResultCode(resultCode);
            dto.setMessage("Successful.");
            dto.setPayType("qr");
            dto.setResponseTime("2026-09-04 10:00:00");
            dto.setExtraData(Base64.getEncoder()
                    .encodeToString(USER_ID.getBytes(StandardCharsets.UTF_8)));

            String raw = "accessKey=access-key"
                    + "&amount=" + dto.getAmount()
                    + "&extraData=" + dto.getExtraData()
                    + "&message=" + dto.getMessage()
                    + "&orderId=" + dto.getOrderId()
                    + "&orderInfo=" + dto.getOrderInfo()
                    + "&orderType=" + dto.getOrderType()
                    + "&partnerCode=" + dto.getPartnerCode()
                    + "&payType=" + dto.getPayType()
                    + "&requestId=" + dto.getRequestId()
                    + "&responseTime=" + dto.getResponseTime()
                    + "&resultCode=" + dto.getResultCode()
                    + "&transId=" + dto.getTransId();

            dto.setSignature(HmacSHA256Util.encode("secret-key", raw));
            return dto;
        }

        private void stubBookingDetail(Map<String, Object> body) {
            when(restTemplate.exchange(contains("/api/v1/booking/"), eq(HttpMethod.GET),
                    any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));
        }

        @Test
        void invalidSignature_throwsAndTouchesNothing() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            dto.setSignature("bogus-signature");

            assertThatThrownBy(() -> service.processIpn(dto))
                    .hasMessageContaining("Chữ ký IPN không hợp lệ");

            verify(paymentTransactionRepository, never()).save(any());
        }

        @Test
        void paymentTransactionNotFound_throws() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.processIpn(dto))
                    .hasMessageContaining("Không tìm thấy payment transaction");
        }

        @Test
        void paymentAlreadySuccess_isIdempotentNoOp() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(payment(PaymentStatus.SUCCESS, "9001")));

            service.processIpn(dto);

            verify(paymentTransactionRepository, never()).save(any());
        }

        @Test
        void transIdAlreadyProcessed_isIdempotentNoOp() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(payment(PaymentStatus.INIT, null)));
            when(paymentTransactionRepository.existsByTransIdAndStatus("9001", PaymentStatus.SUCCESS))
                    .thenReturn(true);

            service.processIpn(dto);

            verify(paymentTransactionRepository, never()).save(any());
        }

        @Test
        void momoResultCodeFailure_marksPaymentFailed() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 1006, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(p.getNextRetryAt()).isNull();
            verify(paymentTransactionRepository).save(p);
        }

        @Test
        void momoResultCode1005_marksPaymentExpired() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 1005, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        }

        @Test
        void orderIdBookingMismatch_marksPaymentReview() {
            MoMoIpnDTO dto = signedIpn("other-booking_123", "200000", 0, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            when(paymentTransactionRepository.findByOrderIdForUpdate("other-booking_123"))
                    .thenReturn(Optional.of(p));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAYMENT_REVIEW);
            assertThat(p.getLastError()).contains("orderId không khớp");
        }

        @Test
        void amountMismatch_marksPaymentReview() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "999999", 0, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAYMENT_REVIEW);
            assertThat(p.getLastError()).contains("Số tiền MoMo không khớp");
        }

        @Test
        void bookingAlreadyPaid_marksPaymentSuccess() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            PaymentTransaction p = payment(PaymentStatus.CONFIRM_PENDING, null);
            p.setEmailSent(true); // bỏ qua nhánh gửi email để test tập trung
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));
            stubBookingDetail(Map.of("status", "PAID", "totalPrice", 200000));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(p.getTransId()).isEqualTo("9001");
        }

        @Test
        void bookingCancelledAfterMomoPaid_marksRefundRequired() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));
            stubBookingDetail(Map.of("status", "CANCELLED", "totalPrice", 200000));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.REFUND_REQUIRED);
        }

        @Test
        void bookingStillPending_movesToConfirmPendingAndPublishesConfirm() {
            MoMoIpnDTO dto = signedIpn("booking-1_123", "200000", 0, "9001");
            PaymentTransaction p = payment(PaymentStatus.INIT, null);
            p.setRetryCount(0);
            when(paymentTransactionRepository.findByOrderIdForUpdate("booking-1_123"))
                    .thenReturn(Optional.of(p));
            stubBookingDetail(Map.of("status", "PENDING", "totalPrice", 200000, "expiresInSeconds", 300));

            service.processIpn(dto);

            assertThat(p.getStatus()).isEqualTo(PaymentStatus.CONFIRM_PENDING);
            assertThat(p.getTransId()).isEqualTo("9001");
            verify(bookingConfirmRequestPublisher).publish(any());
        }
    }
}
