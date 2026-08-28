package com.movie.payment_service.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "refund_transactions",
        indexes = {
                @Index(name = "idx_refund_payment_transaction_id", columnList = "paymentTransactionId"),
                @Index(name = "idx_refund_booking_id", columnList = "bookingId")
        }
)
@Data
public class RefundTransaction {

    @Id
    private String id;

    @Column(nullable = false)
    private String paymentTransactionId;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    // Mã giao dịch hoàn tiền phía MoMo trả về khi hoàn thành công
    private String momoRefundTransId;

    @Column(length = 2000)
    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime refundedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = RefundStatus.PENDING;
        }
    }
}
