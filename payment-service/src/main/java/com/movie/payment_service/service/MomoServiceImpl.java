package com.movie.payment_service.service;

import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.entity.PaymentStatus;
import com.movie.payment_service.entity.PaymentTransaction;
import com.movie.payment_service.repository.PaymentTransactionRepository;
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

    @Value("${momo.return-url}")
    private String returnUrl;

    @Value("${momo.notify-url}")
    private String notifyUrl;

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Value("${app.payment-retry.max-retry-count:10}")
    private int maxRetryCount;

    private static final String BOOKING_SERVICE_BASE_URL = "http://localhost:8082/api/v1/booking";

    @Value("${app.auth-service-url:http://localhost:8083}")
    private String authServiceUrl;

    @Value("${app.notification-service-url:http://localhost:8085}")
    private String notificationServiceUrl;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional
    public String createPayment(String userId, String bookingId) {
        Map<String, Object> bookingDetail = getBookingDetail(userId, bookingId);

        validateBookingCanCreatePayment(bookingDetail);

        Long amountLong = extractAmountFromBooking(bookingDetail);

        PaymentTransaction existingPayment = paymentTransactionRepository
                .findByBookingId(bookingId)
                .orElse(null);

        if (existingPayment != null) {
            if (PaymentStatus.SUCCESS.equals(existingPayment.getStatus())) {
                throw new RuntimeException("Hóa đơn này đã thanh toán, không thể tạo QR mới!");
            }

            if (PaymentStatus.INIT.equals(existingPayment.getStatus())
                    && existingPayment.getPayUrl() != null
                    && !existingPayment.getPayUrl().isBlank()) {
                return existingPayment.getPayUrl();
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

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response = restTemplate.postForObject(endpoint, requestEntity, Map.class);

        if (response == null || !response.containsKey("payUrl")) {
            throw new RuntimeException("Tạo mã thanh toán MoMo thất bại! Response=" + response);
        }

        String payUrl = response.get("payUrl").toString();

        try {
            PaymentTransaction payment = existingPayment != null ? existingPayment : new PaymentTransaction();

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

        if (dto.getResultCode() == null || dto.getResultCode() != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setTransId(dto.getTransId());
            paymentTransactionRepository.save(payment);

            System.out.println(">>> Thanh toán MoMo thất bại hoặc khách hủy. orderId=" + dto.getOrderId());
            return;
        }

        String userId = decodeExtraData(dto.getExtraData());

        if (!bookingId.equals(payment.getBookingId())) {
            throw new RuntimeException("orderId không khớp bookingId trong payment transaction!");
        }

        if (!userId.equals(payment.getUserId())) {
            throw new RuntimeException("extraData userId không khớp payment transaction!");
        }

        Long momoAmount = Long.parseLong(dto.getAmount());

        if (!payment.getAmount().equals(momoAmount)) {
            throw new RuntimeException("Số tiền MoMo không khớp payment transaction! expected="
                    + payment.getAmount() + ", actual=" + momoAmount);
        }

        Map<String, Object> bookingDetail = getBookingDetail(userId, bookingId);
        Long bookingAmount = extractAmountFromBooking(bookingDetail);

        if (!bookingAmount.equals(momoAmount)) {
            throw new RuntimeException("Số tiền MoMo không khớp booking! bookingAmount="
                    + bookingAmount + ", momoAmount=" + momoAmount);
        }

        String bookingStatus = String.valueOf(bookingDetail.get("status"));

        if ("PAID".equalsIgnoreCase(bookingStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setTransId(dto.getTransId());

            if (payment.getPaidAt() == null) {
                payment.setPaidAt(LocalDateTime.now());
            }

            paymentTransactionRepository.save(payment);

            sendBookingPaidEmailIfNeeded(payment);

            System.out.println(">>> Booking đã PAID trước đó. Đánh dấu payment SUCCESS và bỏ qua confirm.");
            return;
        }

        if (!"PENDING".equalsIgnoreCase(bookingStatus)) {
            throw new RuntimeException("Booking không còn ở trạng thái PENDING. Status hiện tại: " + bookingStatus);
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
        RestTemplate restTemplate = new RestTemplate();

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

    private void callBookingConfirm(String userId, String bookingId) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        String bookingServiceUrl = BOOKING_SERVICE_BASE_URL + "/" + bookingId + "/confirm";

        restTemplate.exchange(
                bookingServiceUrl,
                HttpMethod.PUT,
                entity,
                String.class
        );
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
    public void confirmBookingAndMarkSuccess(PaymentTransaction payment) {
        try {
            callBookingConfirm(payment.getUserId(), payment.getBookingId());

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

        } catch (Exception ex) {
            int currentRetry = payment.getRetryCount() == null ? 0 : payment.getRetryCount();
            int nextRetry = currentRetry + 1;

            payment.setRetryCount(nextRetry);
            payment.setStatus(PaymentStatus.CONFIRM_PENDING);
            payment.setLastError(ex.getMessage());

            if (nextRetry >= maxRetryCount) {
                payment.setNextRetryAt(LocalDateTime.now().plusMinutes(30));
            } else {
                payment.setNextRetryAt(LocalDateTime.now().plusMinutes(calculateBackoffMinutes(nextRetry)));
            }

            paymentTransactionRepository.save(payment);

            System.err.println(">>> [CẦN RETRY] MoMo đã thanh toán nhưng confirm booking lỗi. bookingId="
                    + payment.getBookingId()
                    + ", retryCount="
                    + nextRetry
                    + ", error="
                    + ex.getMessage());
        }
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

    private String getUserEmail(String userId) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                authServiceUrl + "/api/v1/auth/internal/users/" + userId,
                HttpMethod.GET,
                entity,
                Map.class
        );

        Map<String, Object> body = response.getBody();

        if (body == null || body.get("email") == null) {
            throw new RuntimeException("Không lấy được email user từ auth-service!");
        }

        return body.get("email").toString();
    }

    private void sendBookingPaidEmailIfNeeded(PaymentTransaction payment) {
        if (payment == null) {
            return;
        }

        if (Boolean.TRUE.equals(payment.getEmailSent())) {
            return;
        }

        try {
            String email = getUserEmail(payment.getUserId());

            Map<String, Object> bookingDetail = getBookingDetail(
                    payment.getUserId(),
                    payment.getBookingId()
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("toEmail", email);
            requestBody.put("bookingId", payment.getBookingId());
            requestBody.put("amount", payment.getAmount());
            requestBody.put(
                    "paidAt",
                    payment.getPaidAt() == null
                            ? LocalDateTime.now().toString()
                            : payment.getPaidAt().toString()
            );

            requestBody.put("seats", buildEmailSeatItems(bookingDetail));
            requestBody.put("snacks", buildEmailSnackItems(bookingDetail));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Secret", internalSecret);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            RestTemplate restTemplate = new RestTemplate();

            restTemplate.exchange(
                    notificationServiceUrl + "/api/v1/notifications/internal/booking-paid",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            payment.setEmailSent(true);
            payment.setEmailSentAt(LocalDateTime.now());
            payment.setEmailError(null);
            paymentTransactionRepository.save(payment);

            System.out.println(">>> Đã gửi email xác nhận thanh toán cho user " + payment.getUserId());

        } catch (Exception e) {
            payment.setEmailSent(false);
            payment.setEmailError(e.getMessage());
            paymentTransactionRepository.save(payment);

            System.err.println(">>> Gửi email thanh toán thất bại. bookingId="
                    + payment.getBookingId()
                    + ", error="
                    + e.getMessage());
        }
    }

    private List<Map<String, Object>> buildEmailSeatItems(Map<String, Object> bookingDetail) {
        List<Map<String, Object>> result = new ArrayList<>();

        Object seatsObj = bookingDetail.get("seats");

        if (!(seatsObj instanceof List<?> seats)) {
            return result;
        }

        for (Object item : seats) {
            if (!(item instanceof Map<?, ?> seatMap)) {
                continue;
            }

            Map<String, Object> seat = new HashMap<>();

            Object seatId = seatMap.get("seatId");
            Object seatName = seatMap.get("seatName");
            Object price = seatMap.get("priceAtPurchase");

            seat.put("seatId", seatId == null ? "" : seatId.toString());
            seat.put("seatName", seatName == null ? seat.get("seatId") : seatName.toString());
            seat.put("price", price == null ? 0L : new BigDecimal(price.toString()).longValue());

            result.add(seat);
        }

        return result;
    }

    private List<Map<String, Object>> buildEmailSnackItems(Map<String, Object> bookingDetail) {
        List<Map<String, Object>> result = new ArrayList<>();

        Object snacksObj = bookingDetail.get("snacks");

        if (!(snacksObj instanceof List<?> snacks)) {
            return result;
        }

        for (Object item : snacks) {
            if (!(item instanceof Map<?, ?> snackMap)) {
                continue;
            }

            Map<String, Object> snack = new HashMap<>();

            Object snackId = snackMap.get("snackId");
            Object snackName = snackMap.get("snackName");
            Object quantity = snackMap.get("quantity");
            Object price = snackMap.get("priceAtPurchase");

            snack.put("snackId", snackId == null ? "" : snackId.toString());
            snack.put("snackName", snackName == null ? snack.get("snackId") : snackName.toString());
            snack.put("quantity", quantity == null ? 0 : Integer.parseInt(quantity.toString()));
            snack.put("price", price == null ? 0L : new BigDecimal(price.toString()).longValue());

            result.add(snack);
        }

        return result;
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
}