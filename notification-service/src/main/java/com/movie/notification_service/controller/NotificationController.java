package com.movie.notification_service.controller;

import com.movie.notification_service.dto.BookingPaidEmailRequest;
import com.movie.notification_service.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final EmailService emailService;

    public NotificationController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/internal/booking-paid")
    public ResponseEntity<Map<String, String>> sendBookingPaidEmail(
            @Valid @RequestBody BookingPaidEmailRequest request
    ) {
        emailService.sendBookingPaidEmail(request);

        return ResponseEntity.ok(Map.of(
                "message", "Email xác nhận thanh toán đã được gửi"
        ));
    }
}