package com.movie.payment_service.controller;

import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.dto.PaymentRequestDTO;
import com.movie.payment_service.service.MomoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment/momo")
public class PaymentController {
    @Autowired
    MomoService moMoService;

    // API này Frontend sẽ gọi để lấy link QR MoMo
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody PaymentRequestDTO request) {

        String payUrl = moMoService.createPayment(userId, request.getBookingId());

        Map<String, String> response = new HashMap<>();
        response.put("payUrl", payUrl);

        return ResponseEntity.ok(response);
    }

    // MoMo sẽ bắn một lệnh POST chứa JSON vào đây ngầm dưới Background
    @PostMapping("/ipn")
    public ResponseEntity<Void> handleMoMoIpn(@RequestBody MoMoIpnDTO ipnData) {

        System.out.println(">>> Nhận được tín hiệu IPN từ MoMo cho đơn hàng: " + ipnData.getOrderId());

        // Gọi Service xử lý
        moMoService.processIpn(ipnData);

        // MoMo yêu cầu phải trả về HTTP 204 (No Content) để xác nhận là Backend đã ghi nhận thành công,
        // nếu không MoMo sẽ liên tục gọi lại (Retry) gây rác hệ thống.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test/{bookingId}/success")
    public ResponseEntity<Map<String, String>> testPaymentSuccess(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String bookingId
    ) {
        moMoService.testConfirmSuccess(userId, bookingId);

        return ResponseEntity.ok(Map.of(
                "message", "Đã giả lập thanh toán thành công và gửi email nếu đủ điều kiện",
                "bookingId", bookingId
        ));
    }
}
