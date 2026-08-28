package com.movie.payment_service.service;

import com.movie.payment_service.dto.BookingConfirmRequestEvent;
import com.movie.payment_service.dto.BookingConfirmResultEvent;
import com.movie.payment_service.dto.BookingPaidEmailEvent;
import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.dto.PaymentTransactionSummaryDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MomoServiceImpl implements MomoService {

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;

    @Value("${momo.secret-key}")
    private String secretKey;

    @Value("${momo.endpoint}")
    private String endpoint;

    @Value("${momo.refund-endpoint}")
    private String refundEndpoint;

    @Value("${momo.return-url}")
    private String returnUrl;

    @Value("${momo.notify-url}")
    private String notifyUrl;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Value("${app.payment-retry.max-retry-count:10}")
    private int maxRetryCount;

    private static final String BOOKING_SERVICE_BASE_URL = "http://localhost:8082/api/v1/booking";

    @Value("${app.auth-service-url:http://localhost:8083/api/v1/auth}")
    private String authServiceUrl;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private RefundTransactionRepository refundTransactionRepository;

    @Autowired
    private BookingPaidEmailPublisher bookingPaidEmailPublisher;

    @Autowired
    private BookingConfirmRequestPublisher bookingConfirmRequestPublisher;

    @Autowired
    private RestTemplate restTemplate;

    private static final long MOMO_QR_TTL_MINUTES = 10;

    @Override
    @Transactional
    public String createPayment(String userId, String bookingId) {
        Map<String, Object> bookingDetail = getBookingDetail(userId, bookingId);

        String bookingStatus = String.valueOf(bookingDetail.get("status"));

        if ("PAID".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Hóa đơn này đã được thanh toán!");
        }

        if ("CANCELLED".equalsIgnoreCase(bookingStatus)
                || "EXPIRED".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Hóa đơn đã hết hạn hoặc đã bị hủy, vui lòng đặt vé lại!");
        }

        if (!"PENDING".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Hóa đơn không ở trạng thái chờ thanh toán. Trạng thái hiện tại: "
                    + bookingStatus);
        }

        Long expiresInSeconds = extractExpiresInSeconds(bookingDetail);

        if (expiresInSeconds != null && expiresInSeconds <= 0) {
            throw new RuntimeException("Hóa đơn đã quá thời gian thanh toán, vui lòng đặt vé lại!");
        }

        validateBookingCanCreatePayment(bookingDetail);

        Long amountLong = extractAmountFromBooking(bookingDetail);

        PaymentTransaction existingPayment = paymentTransactionRepository
                .findByBookingId(bookingId)
                .orElse(null);

        boolean createNewQrForExpiredPayment = false;

        if (existingPayment != null) {
            if (PaymentStatus.SUCCESS.equals(existingPayment.getStatus())) {
                throw new RuntimeException("Hóa đơn này đã thanh toán, không thể tạo QR mới!");
            }

            if (PaymentStatus.CONFIRM_PENDING.equals(existingPayment.getStatus())) {
                throw new RuntimeException("Giao dịch đang được hệ thống xác nhận, vui lòng chờ trong giây lát!");
            }

            if (PaymentStatus.PAYMENT_REVIEW.equals(existingPayment.getStatus())) {
                throw new RuntimeException("Giao dịch này đang cần kiểm tra thủ công, vui lòng liên hệ hỗ trợ!");
            }

            if (PaymentStatus.REFUND_REQUIRED.equals(existingPayment.getStatus())) {
                throw new RuntimeException("Giao dịch này cần xử lý hoàn tiền, không thể tạo QR mới!");
            }

            if (PaymentStatus.INIT.equals(existingPayment.getStatus())
                    && existingPayment.getPayUrl() != null
                    && !existingPayment.getPayUrl().isBlank()) {

                if (isPaymentQrExpired(existingPayment)) {
                    existingPayment.setStatus(PaymentStatus.EXPIRED);
                    existingPayment.setLastError("QR thanh toán cũ đã hết hạn, hệ thống sẽ tạo QR mới.");
                    existingPayment.setNextRetryAt(null);
                    paymentTransactionRepository.save(existingPayment);
                    createNewQrForExpiredPayment = true;
                    System.out.println(">>> QR cũ đã hết hạn. Tạo payment mới cho bookingId="
                            + bookingId);
                } else {
                    return existingPayment.getPayUrl();
                }
            }
        }

        String orderId = bookingId + "_" + System.currentTimeMillis();
        String requestId = orderId;
        String orderInfo = "Thanh toan ve phim";

        String extraData = Base64.getEncoder()
                .encodeToString(userId.trim().getBytes(StandardCharsets.UTF_8));

        String rawSignature =
                "accessKey=" + accessKey
                + "&amount=" + amountLong
                + "&extraData=" + extraData
                + "&ipnUrl=" + notifyUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + returnUrl
                + "&requestId=" + requestId
                + "&requestType=captureWallet";

        String signature = hmacSHA256(rawSignature, secretKey);


        System.out.println("========== MOMO RAW SIGNATURE ==========");
        System.out.println(rawSignature);
        System.out.println("========== MOMO SIGNATURE ==========");
        System.out.println(signature);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", partnerCode);
        requestBody.put("partnerName", "Rap Phim Microservices");
        requestBody.put("storeId", "MomoTestStore");
        requestBody.put("requestId", requestId);
        requestBody.put("amount", amountLong);
        requestBody.put("orderId", orderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", returnUrl);
        requestBody.put("ipnUrl", notifyUrl);
        requestBody.put("lang", "vi");
        requestBody.put("requestType", "captureWallet");
        requestBody.put("autoCapture", true);
        requestBody.put("extraData", extraData);
        requestBody.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        Map<String, Object> response = restTemplate.postForObject(endpoint, requestEntity, Map.class);

        if (response == null || !response.containsKey("payUrl")) {
            throw new RuntimeException("Tạo mã thanh toán MoMo thất bại! Response=" + response);
        }

        String payUrl = response.get("payUrl").toString();

        try {
            PaymentTransaction payment = existingPayment != null ? existingPayment : new PaymentTransaction();

            if (createNewQrForExpiredPayment) {
                payment.setCreatedAt(LocalDateTime.now());
            }

            payment.setBookingId(bookingId);
            payment.setUserId(userId);
            payment.setOrderId(orderId);
            payment.setRequestId(requestId);
            payment.setAmount(amountLong);
            payment.setPayUrl(payUrl);
            payment.setStatus(PaymentStatus.INIT);

            // Nếu trước đó payment FAILED và tạo lại QR mới thì reset dữ liệu cũ.
            payment.setTransId(null);
            payment.setPaidAt(null);

            paymentTransactionRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            PaymentTransaction duplicated = paymentTransactionRepository.findByBookingId(bookingId)
                    .orElseThrow(() -> new RuntimeException("Đang có giao dịch thanh toán khác cho booking này!"));

            if (duplicated.getPayUrl() != null && !duplicated.getPayUrl().isBlank()) {
                return duplicated.getPayUrl();
            }

            throw new RuntimeException("Đang có giao dịch thanh toán khác cho booking này!");
        }

        return payUrl;
    }

    @Override
    @Transactional
    public void processIpn(MoMoIpnDTO dto) {
        validateMomoSignature(dto);

        String bookingId = extractBookingIdFromOrderId(dto.getOrderId());

        PaymentTransaction payment = paymentTransactionRepository
                .findByOrderIdForUpdate(dto.getOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy payment transaction cho orderId: " + dto.getOrderId()));

        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            System.out.println(">>> Payment orderId " + dto.getOrderId() + " đã SUCCESS trước đó. Bỏ qua IPN lặp.");
            return;
        }

        if (dto.getTransId() != null
                && !dto.getTransId().isBlank()
                && paymentTransactionRepository.existsByTransIdAndStatus(dto.getTransId(), PaymentStatus.SUCCESS)) {
            System.out.println(">>> MoMo transId " + dto.getTransId() + " đã xử lý thành công trước đó. Bỏ qua IPN lặp.");
            return;
        }

        String resultCode = String.valueOf(dto.getResultCode());

        if (!"0".equals(resultCode)) {
            if ("1005".equals(resultCode)) {
                payment.setStatus(PaymentStatus.EXPIRED);
                payment.setLastError("MoMo báo QR hoặc URL thanh toán đã hết hạn. resultCode=1005");
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setLastError("MoMo thanh toán thất bại. resultCode="
                        + resultCode
                        + ", message="
                        + dto.getMessage());
            }

            payment.setNextRetryAt(null);
            paymentTransactionRepository.save(payment);

            System.out.println(">>> MoMo trả thanh toán thất bại. bookingId="
                    + payment.getBookingId()
                    + ", resultCode="
                    + resultCode);

            return;
        }

        String userId = decodeExtraData(dto.getExtraData());

        if (!bookingId.equals(payment.getBookingId())) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "orderId không khớp bookingId trong payment transaction!"
            );
            return;
        }

        if (!userId.equals(payment.getUserId())) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "extraData userId không khớp payment transaction!"
            );
            return;
        }

        Long momoAmount = Long.parseLong(dto.getAmount());

        if (!payment.getAmount().equals(momoAmount)) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "Số tiền MoMo không khớp payment transaction! expected="
                            + payment.getAmount()
                            + ", actual="
                            + momoAmount
            );
            return;
        }

        Map<String, Object> bookingDetail;

        try {
            bookingDetail = getBookingDetail(userId, bookingId);
        } catch (Exception ex) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "MoMo đã thanh toán nhưng không lấy được booking detail: " + ex.getMessage()
            );
            return;
        }

        Long bookingAmount = extractAmountFromBooking(bookingDetail);

        if (!bookingAmount.equals(momoAmount)) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "Số tiền MoMo không khớp booking! bookingAmount="
                            + bookingAmount
                            + ", momoAmount="
                            + momoAmount
            );
            return;
        }

        String bookingStatus = String.valueOf(bookingDetail.get("status"));

        if ("PAID".equalsIgnoreCase(bookingStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransId(dto.getTransId());

            if (payment.getPaidAt() == null) {
                payment.setPaidAt(LocalDateTime.now());
            }

            payment.setLastError(null);
            payment.setNextRetryAt(null);
            paymentTransactionRepository.save(payment);

            sendBookingPaidEmailIfNeeded(payment);

            System.out.println(">>> Booking đã PAID trước đó. Đánh dấu payment SUCCESS.");
            return;
        }

        if ("CANCELLED".equalsIgnoreCase(bookingStatus)
                || "EXPIRED".equalsIgnoreCase(bookingStatus)) {
            markRefundRequired(
                    payment,
                    dto.getTransId(),
                    "MoMo đã thanh toán thành công nhưng booking đã "
                            + bookingStatus
                            + ". Cần xử lý hoàn tiền."
            );
            return;
        }

        if (!"PENDING".equalsIgnoreCase(bookingStatus)) {
            markPaymentReview(
                    payment,
                    dto.getTransId(),
                    "MoMo đã thanh toán nhưng booking không còn ở trạng thái PENDING. Status hiện tại: "
                            + bookingStatus
            );
            return;
        }

        Long expiresInSeconds = extractExpiresInSeconds(bookingDetail);

        if (expiresInSeconds != null && expiresInSeconds <= 0) {
            markRefundRequired(
                    payment,
                    dto.getTransId(),
                    "MoMo đã thanh toán thành công nhưng booking đã quá thời gian thanh toán. Cần xử lý hoàn tiền."
            );
            return;
        }

        payment.setStatus(PaymentStatus.CONFIRM_PENDING);
        payment.setTransId(dto.getTransId());
        payment.setPaidAt(LocalDateTime.now());
        payment.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        payment.setLastError(null);
        paymentTransactionRepository.save(payment);

        confirmBookingAndMarkSuccess(payment);

        System.out.println(">>> [THÀNH CÔNG] Đã ghi nhận thanh toán MoMo cho hóa đơn " + bookingId);
    }

    @Override
    @Transactional
    public void testConfirmSuccess(String userId, String bookingId) {
        Map<String, Object> bookingDetail = getBookingDetail(userId, bookingId);

        if (bookingDetail == null) {
            throw new RuntimeException("Không lấy được thông tin booking!");
        }

        String bookingStatus = String.valueOf(bookingDetail.get("status"));

        if ("CANCELLED".equalsIgnoreCase(bookingStatus)
                || "EXPIRED".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Booking đã bị hủy hoặc hết hạn, không thể giả lập thanh toán!");
        }

        Long amount = extractAmountFromBooking(bookingDetail);

        PaymentTransaction payment = paymentTransactionRepository
                .findByBookingId(bookingId)
                .orElseGet(PaymentTransaction::new);

        payment.setBookingId(bookingId);
        payment.setUserId(userId);
        payment.setAmount(amount);

        if (payment.getOrderId() == null || payment.getOrderId().isBlank()) {
            String testOrderId = bookingId + "_TEST_" + System.currentTimeMillis();
            payment.setOrderId(testOrderId);
            payment.setRequestId(testOrderId);
        }

        if (payment.getRequestId() == null || payment.getRequestId().isBlank()) {
            payment.setRequestId(payment.getOrderId());
        }

        payment.setTransId("TEST_TRANS_" + System.currentTimeMillis());
        payment.setPaidAt(LocalDateTime.now());
        payment.setStatus(PaymentStatus.CONFIRM_PENDING);
        payment.setLastError(null);
        payment.setNextRetryAt(null);

        paymentTransactionRepository.save(payment);

        if ("PAID".equalsIgnoreCase(bookingStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            paymentTransactionRepository.save(payment);

            sendBookingPaidEmailIfNeeded(payment);
            return;
        }

        if (!"PENDING".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Booking không ở trạng thái PENDING hoặc PAID. Status hiện tại: " + bookingStatus);
        }

        confirmBookingAndMarkSuccess(payment);
    }

    @Override
    public List<PaymentTransactionSummaryDTO> getMyTransactions(String userId) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(payment -> PaymentTransactionSummaryDTO.builder()
                        .transactionId(payment.getId())
                        .bookingId(payment.getBookingId())
                        .amount(payment.getAmount())
                        .status(payment.getStatus())
                        .createdAt(payment.getCreatedAt())
                        .paidAt(payment.getPaidAt())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public RefundResponseDTO refundPayment(String bookingId, String reason) {
        PaymentTransaction payment = paymentTransactionRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch thanh toán cho booking này!"));

        if (!PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            throw new RuntimeException("Giao dịch này chưa thanh toán thành công, không thể hoàn tiền!");
        }

        RefundTransaction existingRefund = refundTransactionRepository
                .findByPaymentTransactionId(payment.getId())
                .orElse(null);

        // Idempotent: đã hoàn tiền thành công trước đó thì không gọi lại MoMo nữa
        if (existingRefund != null && RefundStatus.SUCCESS.equals(existingRefund.getStatus())) {
            return RefundResponseDTO.builder()
                    .refundTransactionId(existingRefund.getId())
                    .bookingId(bookingId)
                    .amount(existingRefund.getAmount())
                    .status(RefundStatus.SUCCESS)
                    .message("Booking này đã được hoàn tiền trước đó.")
                    .build();
        }

        RefundTransaction refund = existingRefund != null ? existingRefund : new RefundTransaction();
        refund.setPaymentTransactionId(payment.getId());
        refund.setBookingId(bookingId);
        refund.setAmount(payment.getAmount());
        refund.setReason(reason);
        refund.setStatus(RefundStatus.PENDING);
        refund.setLastError(null);
        refundTransactionRepository.save(refund);

        Long transIdLong;
        try {
            transIdLong = Long.parseLong(payment.getTransId());
        } catch (Exception ex) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setLastError("Giao dịch không có mã transId MoMo hợp lệ (có thể là giao dịch giả lập test), không thể hoàn tiền tự động!");
            refundTransactionRepository.save(refund);

            return RefundResponseDTO.builder()
                    .refundTransactionId(refund.getId())
                    .bookingId(bookingId)
                    .amount(refund.getAmount())
                    .status(RefundStatus.FAILED)
                    .message(refund.getLastError())
                    .build();
        }

        String refundOrderId = "REFUND_" + payment.getOrderId() + "_" + System.currentTimeMillis();
        String requestId = refundOrderId;
        String description = "Hoan tien huy ve" + (reason != null && !reason.isBlank() ? ": " + reason : "");

        String rawSignature =
                "accessKey=" + accessKey
                + "&amount=" + payment.getAmount()
                + "&description=" + description
                + "&orderId=" + refundOrderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId
                + "&transId=" + transIdLong;

        String signature = hmacSHA256(rawSignature, secretKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", partnerCode);
        requestBody.put("orderId", refundOrderId);
        requestBody.put("requestId", requestId);
        requestBody.put("amount", payment.getAmount());
        requestBody.put("transId", transIdLong);
        requestBody.put("lang", "vi");
        requestBody.put("description", description);
        requestBody.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    refundEndpoint, new HttpEntity<>(requestBody, headers), Map.class);

            Object resultCodeObj = response != null ? response.get("resultCode") : null;
            boolean success = resultCodeObj != null && Integer.parseInt(resultCodeObj.toString()) == 0;

            if (success) {
                refund.setStatus(RefundStatus.SUCCESS);
                refund.setRefundedAt(LocalDateTime.now());
                Object momoTransId = response.get("transId");
                refund.setMomoRefundTransId(momoTransId != null ? momoTransId.toString() : null);
            } else {
                refund.setStatus(RefundStatus.FAILED);
                refund.setLastError("MoMo từ chối hoàn tiền, response=" + response);
            }
        } catch (Exception ex) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setLastError("Lỗi khi gọi API hoàn tiền MoMo: " + ex.getMessage());
        }

        refundTransactionRepository.save(refund);

        return RefundResponseDTO.builder()
                .refundTransactionId(refund.getId())
                .bookingId(bookingId)
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .message(RefundStatus.SUCCESS.equals(refund.getStatus())
                        ? "Hoàn tiền thành công!"
                        : "Hoàn tiền tự động thất bại: " + refund.getLastError())
                .build();
    }

    private void validateMomoSignature(MoMoIpnDTO dto) {
        String rawHash = "accessKey=" + accessKey
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

        String mySignature = HmacSHA256Util.encode(secretKey, rawHash);

        if (!mySignature.equals(dto.getSignature())) {
            throw new RuntimeException("CẢNH BÁO: Chữ ký IPN không hợp lệ. Đã chặn giao dịch giả mạo!");
        }
    }

    private String extractBookingIdFromOrderId(String orderId) {
        if (orderId == null || orderId.isBlank() || !orderId.contains("_")) {
            throw new RuntimeException("orderId không hợp lệ. orderId phải có dạng bookingId_timestamp");
        }

        return orderId.split("_")[0];
    }

    private Map<String, Object> getBookingDetail(String userId, String bookingId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BOOKING_SERVICE_BASE_URL + "/" + bookingId,
                HttpMethod.GET,
                entity,
                Map.class
        );

        return response.getBody();
    }

    private void validateBookingCanCreatePayment(Map<String, Object> bookingDetail) {
        if (bookingDetail == null) {
            throw new RuntimeException("Không lấy được thông tin booking!");
        }

        String status = String.valueOf(bookingDetail.get("status"));

        if ("PAID".equalsIgnoreCase(status)) {
            throw new RuntimeException("Hóa đơn này đã thanh toán, không thể tạo QR mới!");
        }

        if ("CANCELLED".equalsIgnoreCase(status)) {
            throw new RuntimeException("Hóa đơn này đã bị hủy hoặc hết hạn, không thể thanh toán!");
        }

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new RuntimeException("Hóa đơn không ở trạng thái chờ thanh toán!");
        }

        Object expiresInSecondsObj = bookingDetail.get("expiresInSeconds");

        if (expiresInSecondsObj != null) {
            long expiresInSeconds = Long.parseLong(expiresInSecondsObj.toString());

            if (expiresInSeconds <= 0) {
                throw new RuntimeException("Hóa đơn đã quá thời gian thanh toán, không thể tạo QR mới!");
            }
        }
    }

    private Long extractAmountFromBooking(Map<String, Object> bookingDetail) {
        if (bookingDetail == null) {
            throw new RuntimeException("Không lấy được thông tin booking!");
        }

        Object totalPriceObj = bookingDetail.get("totalPrice");

        if (totalPriceObj == null) {
            throw new RuntimeException("Booking không có tổng tiền!");
        }

        BigDecimal totalPrice = new BigDecimal(totalPriceObj.toString())
                .setScale(0, RoundingMode.HALF_UP);

        if (totalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Tổng tiền booking không hợp lệ!");
        }

        return totalPrice.longValueExact();
    }

    private String decodeExtraData(String extraData) {
        if (extraData == null || extraData.isBlank()) {
            throw new RuntimeException("IPN thiếu extraData!");
        }

        return new String(Base64.getDecoder().decode(extraData), StandardCharsets.UTF_8);
    }
    /**
     * Gửi yêu cầu confirm booking qua RabbitMQ (không đợi phản hồi đồng bộ).
     * Chủ động đặt sẵn retryCount/nextRetryAt ngay khi gửi, vì kết quả thật sự
     * (thành công hay thất bại) sẽ được báo về sau qua {@link #handleBookingConfirmResult}.
     * Nếu booking-service không phản hồi kịp (mất message, service down...),
     * PaymentConfirmRetryJob sẽ tự gửi lại yêu cầu khi tới nextRetryAt.
     */
    public void confirmBookingAndMarkSuccess(PaymentTransaction payment) {
        int currentRetry = payment.getRetryCount() == null ? 0 : payment.getRetryCount();
        int nextRetry = currentRetry + 1;

        if (nextRetry >= maxRetryCount) {
            payment.setRetryCount(nextRetry);
            payment.setStatus(PaymentStatus.PAYMENT_REVIEW);
            payment.setNextRetryAt(null);
            payment.setLastError("MoMo đã thanh toán nhưng confirm booking quá số lần thử. Chuyển sang review thủ công.");
            paymentTransactionRepository.save(payment);
            return;
        }

        payment.setRetryCount(nextRetry);
        payment.setStatus(PaymentStatus.CONFIRM_PENDING);
        payment.setNextRetryAt(LocalDateTime.now().plusMinutes(calculateBackoffMinutes(nextRetry)));
        paymentTransactionRepository.save(payment);

        try {
            bookingConfirmRequestPublisher.publish(
                    BookingConfirmRequestEvent.builder()
                            .paymentId(payment.getId())
                            .bookingId(payment.getBookingId())
                            .userId(payment.getUserId())
                            .build()
            );

            System.out.println(">>> [ĐÃ GỬI] Yêu cầu confirm booking "
                    + payment.getBookingId()
                    + " cho user "
                    + payment.getUserId());

        } catch (Exception ex) {
            payment.setLastError("Không gửi được yêu cầu confirm booking qua RabbitMQ: " + ex.getMessage());
            paymentTransactionRepository.save(payment);

            System.err.println(">>> [LỖI GỬI] Không publish được yêu cầu confirm booking. bookingId="
                    + payment.getBookingId()
                    + ", error="
                    + ex.getMessage());
        }
    }

    /**
     * Nhận kết quả confirm booking từ booking-service qua RabbitMQ.
     * Chỉ khi nhận được success=true ở đây thì payment mới được đánh dấu SUCCESS.
     */
    @Transactional
    public void handleBookingConfirmResult(BookingConfirmResultEvent event) {
        PaymentTransaction payment = paymentTransactionRepository
                .findById(event.getPaymentId())
                .orElse(null);

        if (payment == null) {
            System.err.println(">>> Nhận kết quả confirm booking nhưng không tìm thấy payment. paymentId="
                    + event.getPaymentId());
            return;
        }

        if (PaymentStatus.SUCCESS.equals(payment.getStatus())) {
            return;
        }

        if (!event.isSuccess()) {
            payment.setLastError(event.getErrorMessage());
            paymentTransactionRepository.save(payment);

            System.err.println(">>> [CẦN RETRY] Booking-service báo confirm thất bại. bookingId="
                    + payment.getBookingId()
                    + ", error="
                    + event.getErrorMessage());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setLastError(null);
        payment.setNextRetryAt(null);

        if (payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }

        paymentTransactionRepository.save(payment);

        sendBookingPaidEmailIfNeeded(payment);

        System.out.println(">>> [THÀNH CÔNG] Đã confirm booking "
                + payment.getBookingId()
                + " cho user "
                + payment.getUserId());
    }

    private long calculateBackoffMinutes(int retryCount) {
        if (retryCount <= 1) {
            return 1;
        }

        if (retryCount == 2) {
            return 2;
        }

        if (retryCount == 3) {
            return 5;
        }

        return 10;
    }

    private void sendBookingPaidEmailIfNeeded(PaymentTransaction payment) {
        if (Boolean.TRUE.equals(payment.getEmailSent())) {
            return;
        }

        try {
            Map<String, Object> bookingDetail = getBookingDetail(
                    payment.getUserId(),
                    payment.getBookingId()
            );

            String toEmail = getUserEmailFromAuth(payment.getUserId());

            BookingPaidEmailEvent event = BookingPaidEmailEvent.builder()
                    .toEmail(toEmail)
                    .bookingId(payment.getBookingId())
                    .amount(payment.getAmount())
                    .paidAt(payment.getPaidAt())
                    .seats(buildSeatItemsFromBookingDetail(bookingDetail))
                    .snacks(buildSnackItemsFromBookingDetail(bookingDetail))
                    .build();

            bookingPaidEmailPublisher.publish(event);

            payment.setEmailSent(true);
            payment.setEmailError(null);
            paymentTransactionRepository.save(payment);

            System.out.println(">>> Đã publish email event qua RabbitMQ. bookingId="
                    + payment.getBookingId());

        } catch (Exception ex) {
            payment.setEmailSent(false);
            payment.setEmailError(ex.getMessage());
            paymentTransactionRepository.save(payment);

            System.err.println(">>> Publish email event thất bại: " + ex.getMessage());
        }
    }

    private String hmacSHA256(String data, String secretKey) {
        try {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            hmacSha256.init(secretKeySpec);

            byte[] hash = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo chữ ký MoMo", e);
        }
    }

    private void markPaymentReview(
            PaymentTransaction payment,
            String transId,
            String reason
    ) {
        payment.setStatus(PaymentStatus.PAYMENT_REVIEW);
        payment.setTransId(transId);

        if (payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }

        payment.setLastError(reason);
        payment.setNextRetryAt(null);

        paymentTransactionRepository.save(payment);

        System.err.println(">>> [PAYMENT_REVIEW] bookingId="
                + payment.getBookingId()
                + ", orderId="
                + payment.getOrderId()
                + ", reason="
                + reason);
    }

    private void markRefundRequired(
            PaymentTransaction payment,
            String transId,
            String reason
    ) {
        payment.setStatus(PaymentStatus.REFUND_REQUIRED);
        payment.setTransId(transId);

        if (payment.getPaidAt() == null) {
            payment.setPaidAt(LocalDateTime.now());
        }

        payment.setLastError(reason);
        payment.setNextRetryAt(null);

        paymentTransactionRepository.save(payment);

        System.err.println(">>> [REFUND_REQUIRED] bookingId="
                + payment.getBookingId()
                + ", orderId="
                + payment.getOrderId()
                + ", reason="
                + reason);
    }
    private List<BookingPaidEmailEvent.SeatItem> buildSeatItemsFromBookingDetail(
            Map<String, Object> bookingDetail
    ) {
        Object seatsObj = bookingDetail.get("seats");

        if (!(seatsObj instanceof List<?> seats)) {
            return List.of();
        }

        return seats.stream().map(item -> {
            Map<String, Object> seat = (Map<String, Object>) item;

            return BookingPaidEmailEvent.SeatItem.builder()
                    .seatId(String.valueOf(seat.get("seatId")))
                    .seatName(String.valueOf(seat.get("seatName")))
                    .price(toLong(seat.get("price")))
                    .build();
        }).toList();
    }

    private List<BookingPaidEmailEvent.SnackItem> buildSnackItemsFromBookingDetail(
            Map<String, Object> bookingDetail
    ) {
        Object snacksObj = bookingDetail.get("snacks");

        if (!(snacksObj instanceof List<?> snacks)) {
            return List.of();
        }

        return snacks.stream().map(item -> {
            Map<String, Object> snack = (Map<String, Object>) item;

            return BookingPaidEmailEvent.SnackItem.builder()
                    .snackId(String.valueOf(snack.get("snackId")))
                    .snackName(String.valueOf(snack.get("snackName")))
                    .quantity(toInteger(snack.get("quantity")))
                    .price(toLong(snack.get("price")))
                    .build();
        }).toList();
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(value.toString());
    }

    private String getUserEmailFromAuth(String userId) {
        String url = authServiceUrl + "/internal/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();

        if (body == null) {
            throw new RuntimeException("Không lấy được thông tin user từ auth-service!");
        }

        if (body.get("email") != null) {
            return body.get("email").toString();
        }

        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> data && data.get("email") != null) {
            return data.get("email").toString();
        }

        throw new RuntimeException("Không lấy được email user từ auth-service!");
    }

    private boolean isPaymentQrExpired(PaymentTransaction payment) {
        if (payment.getCreatedAt() == null) {
            return false;
        }

        return payment.getCreatedAt()
                .plusMinutes(MOMO_QR_TTL_MINUTES)
                .isBefore(LocalDateTime.now());
    }

    private Long extractExpiresInSeconds(Map<String, Object> bookingDetail) {
        Object value = bookingDetail.get("expiresInSeconds");

        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(value.toString());
    }
}