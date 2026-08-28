package com.movie.notification_service.controller;

import com.movie.notification_service.dto.BookingPaidEmailRequest;
import com.movie.notification_service.dto.CreateNotificationRequestDTO;
import com.movie.notification_service.dto.EmailVerificationRequest;
import com.movie.notification_service.dto.NotificationResponseDTO;
import com.movie.notification_service.dto.PasswordResetEmailRequest;
import com.movie.notification_service.service.EmailService;
import com.movie.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final EmailService emailService;
    private final NotificationService notificationService;

    public NotificationController(EmailService emailService, NotificationService notificationService) {
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @GetMapping("/my-notifications")
    public ResponseEntity<List<NotificationResponseDTO>> getMyNotifications(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(Map.of("unreadCount", notificationService.getUnreadCount(userId)));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponseDTO> markAsRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String notificationId) {
        return ResponseEntity.ok(notificationService.markAsRead(userId, notificationId));
    }

    @PostMapping("/internal/create")
    public ResponseEntity<NotificationResponseDTO> createNotification(
            @Valid @RequestBody CreateNotificationRequestDTO request) {
        NotificationResponseDTO response = notificationService.createNotification(
                request.getUserId(), request.getTitle(), request.getContent(), request.getType());
        return ResponseEntity.ok(response);
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