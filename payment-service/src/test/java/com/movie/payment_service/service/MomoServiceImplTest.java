package com.movie.payment_service.service;

import com.movie.payment_service.dto.BookingConfirmResultEvent;
import com.movie.payment_service.dto.RefundResponseDTO;
import com.movie.payment_service.entity.PaymentStatus;
import com.movie.payment_service.entity.PaymentTransaction;
import com.movie.payment_service.entity.RefundStatus;
import com.movie.payment_service.entity.RefundTransaction;
import com.movie.payment_service.publisher.BookingConfirmRequestPublisher;
import com.movie.payment_service.publisher.BookingPaidEmailPublisher;
import com.movie.payment_service.repository.PaymentTransactionRepository;
import com.movie.payment_service.repository.RefundTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
