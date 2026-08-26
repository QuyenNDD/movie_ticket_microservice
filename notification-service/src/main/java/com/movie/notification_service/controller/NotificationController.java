package com.movie.notification_service.controller;

import com.movie.notification_service.dto.BookingPaidEmailRequest;
import com.movie.notification_service.dto.EmailVerificationRequest;
import com.movie.notification_service.dto.PasswordResetEmailRequest;
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

    @PostMapping("/internal/password-reset")
    public ResponseEntity<Map<String, String>> sendPasswordResetEmail(
            @Valid @RequestBody PasswordResetEmailRequest request
    ) {
        emailService.sendPasswordResetEmail(request);

        return ResponseEntity.ok(Map.of(
                "message", "Email đặt lại mật khẩu đã được gửi"
        ));
    }

    @PostMapping("/internal/email-verification")
    public ResponseEntity<Map<String, String>> sendEmailVerification(
            @Valid @RequestBody EmailVerificationRequest request
    ) {
        emailService.sendEmailVerification(request);

        return ResponseEntity.ok(Map.of(
                "message", "Email xác minh đã được gửi"
        ));
    }
}