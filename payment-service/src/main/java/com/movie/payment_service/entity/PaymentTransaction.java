package com.movie.payment_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "payment_transactions",
        indexes = {
                @Index(name = "idx_payment_booking_id", columnList = "bookingId"),
                @Index(name = "idx_payment_order_id", columnList = "orderId"),
                @Index(name = "idx_payment_trans_id", columnList = "transId"),
                @Index(name = "idx_payment_status_next_retry", columnList = "status,nextRetryAt")
        }
)
@Data
public class PaymentTransaction {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String bookingId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(unique = true)
    private String transId;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 2000)
    private String payUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime paidAt;

    private Integer retryCount = 0;

    private LocalDateTime nextRetryAt;

    @Column(length = 2000)
    private String lastError;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        updatedAt = LocalDateTime.now();

        if (retryCount == null) {
            retryCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();

        if (retryCount == null) {
            retryCount = 0;
        }
    }
}