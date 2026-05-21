package com.movie.payment_service.service;

import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.util.HmacSHA256Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class MomoServiceImpl implements MomoService{
//    @Value("${momo.partner-code}")
    private String partnerCode = "MOMO5RGX20191128";

//    @Value("${momo.access-key}")
    private String accessKey = "M8brj9K6E22vXoDB";

//    @Value("${momo.secret-key}")
    private String secretKey = "nqQiVSgDMy809JoPF6OzP5OdBUB550Y4";

//    @Value("${momo.endpoint}")
    private String endpoint = "https://test-payment.momo.vn/v2/gateway/api/create";

//    @Value("${momo.return-url}")
    private String returnUrl = "http://localhost:3000/payment-result";

//    @Value("${momo.notify-url}")
    private String notifyUrl = "https://hungry-pleading-baritone.ngrok-free.dev/api/v1/payment/momo/ipn";

    @Override
    public String createPayment(String userId, String bookingId, String amount) {
        // 1. Tạo OrderId độc nhất (Mã hóa đơn + Timestamp để không bị trùng)
        String orderId = String.valueOf(System.currentTimeMillis());
        String requestId = orderId;
        String orderInfo = "Thanh toan ve phim";

        // 3. extraData BẮT BUỘC PHẢI MÃ HÓA BASE64 THEO CHUẨN MOMO V2
        String extraData = "";
        if (userId != null && !userId.isEmpty()) {
            // Biến chuỗi UUID thành dạng mã hóa (VD: YjVmODhhN2Mt...)
            extraData = Base64.getEncoder().encodeToString(userId.trim().getBytes());
        }

        // 4. Ép amount về kiểu Long (Kiểu số nguyên)
        Long amountLong = Long.parseLong(amount.trim());

        // 5. Ghép chuỗi chuẩn form MoMo (Lưu ý: dùng amountLong và extraData đã mã hóa)
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amountLong
                + "&extraData=" + extraData
                + "&ipnUrl=" + notifyUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + returnUrl
                + "&requestId=" + requestId
                + "&requestType=captureWallet";

        // 6. Ký chữ ký số
        String signature = HmacSHA256Util.encode(secretKey, rawSignature);

        // 7. SỬA THÀNH Map<String, Object> ĐỂ JACKSON GỬI KIỂU NUMBER XUỐNG CHO MOMO
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerCode", partnerCode);
        requestBody.put("partnerName", "Rap Phim Microservices");
        requestBody.put("storeId", "MomoTestStore");
        requestBody.put("requestId", requestId);

        // Gửi thẳng biến kiểu Long vào, Jackson sẽ sinh ra JSON: "amount": 150000 (Không có dấu ngoặc kép)
        requestBody.put("amount", amountLong);

        requestBody.put("orderId", orderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", returnUrl);
        requestBody.put("ipnUrl", notifyUrl);
        requestBody.put("lang", "vi");
        requestBody.put("extraData", extraData);
        requestBody.put("requestType", "captureWallet");
        requestBody.put("signature", signature);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Lưu ý: Nhớ đổi HttpEntity thành Map<String, Object>
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response = restTemplate.postForObject(endpoint, requestEntity, Map.class);

        if (response != null && response.containsKey("payUrl")) {
            System.out.println(">>> LẤY LINK THÀNH CÔNG: " + response.get("payUrl"));
            return response.get("payUrl").toString();
        } else {
            System.err.println(">>> LỖI TỪ MOMO TRẢ VỀ: " + response);
            throw new RuntimeException("Tạo mã thanh toán MoMo thất bại!");
        }
    }

    @Override
    public void processIpn(MoMoIpnDTO dto) {
        // 1. KIỂM TRA BẢO MẬT: Băm lại dữ liệu xem có khớp chữ ký MoMo gửi không
        // Công thức băm của IPN MoMo (Xếp theo thứ tự A-Z)
        String rawHash = "accessKey=" + accessKey +
                "&amount=" + dto.getAmount() +
                "&extraData=" + dto.getExtraData() +
                "&message=" + dto.getMessage() +
                "&orderId=" + dto.getOrderId() +
                "&orderInfo=" + dto.getOrderInfo() +
                "&orderType=" + dto.getOrderType() +
                "&partnerCode=" + dto.getPartnerCode() +
                "&payType=" + dto.getPayType() +
                "&requestId=" + dto.getRequestId() +
                "&responseTime=" + dto.getResponseTime() +
                "&resultCode=" + dto.getResultCode() +
                "&transId=" + dto.getTransId();

        String mySignature = HmacSHA256Util.encode(secretKey, rawHash);

        if (!mySignature.equals(dto.getSignature())) {
            // Nếu chữ ký sai -> Bọn Hacker đang dùng Postman gọi fake vào Webhook của mình!
            throw new RuntimeException("CẢNH BÁO: Chữ ký IPN không hợp lệ. Đã chặn giao dịch giả mạo!");
        }

        // 2. KIỂM TRA TRẠNG THÁI TIỀN VỀ
        if (dto.getResultCode() == 0) {
            System.out.println(">>> TIỀN ĐÃ VỀ TÀI KHOẢN! BẮT ĐẦU CHỐT ĐƠN...");

            // Lấy lại bookingId (lúc nãy ta nối thêm Timestamp bằng dấu _, giờ cắt ra)
            String bookingId = dto.getOrderId().split("_")[0];
            // Lấy lại userId từ cái túi extraData
            String userId = dto.getExtraData();

            // 3. GỌI SANG BOOKING-SERVICE ĐỂ ĐỔI TRẠNG THÁI HÓA ĐƠN THÀNH 'PAID'
            RestTemplate restTemplate = new RestTemplate();

            // Gắn X-User-Id vào Header để lách qua chốt bảo mật của Booking Service
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId);
            HttpEntity<String> entity = new HttpEntity<>(null, headers);

            String bookingServiceUrl = "http://localhost:8082/api/v1/booking/" + bookingId + "/confirm";

            try {
                // Dùng RestTemplate bắn 1 lệnh PUT sang Booking Service
                restTemplate.exchange(bookingServiceUrl, org.springframework.http.HttpMethod.PUT, entity, String.class);
                System.out.println(">>> [THÀNH CÔNG] Đã xác nhận hóa đơn " + bookingId + " cho user " + userId);
            } catch (Exception e) {
                System.err.println(">>> [LỖI] Tiền đã nhận nhưng lỗi khi gọi sang Booking Service: " + e.getMessage());
                // (Thực tế đi làm sẽ cần đẩy vào RabbitMQ để xử lý đền bù (Retry), tạm thời in log)
            }
        } else {
            System.out.println(">>> KHÁCH HÀNG HỦY THANH TOÁN HOẶC LỖI THẺ. Bỏ qua.");
        }
    }
}
