package com.movie.payment_service.entity;

public enum PaymentStatus {
    INIT,
    CONFIRM_PENDING,
    SUCCESS,
    FAILED,
    EXPIRED,

    // MoMo đã có tín hiệu thanh toán nhưng hệ thống cần admin kiểm tra
    PAYMENT_REVIEW,

    // MoMo thanh toán thành công nhưng booking không thể xác nhận vé
    // Ví dụ: booking đã CANCELLED / EXPIRED / không còn PENDING
    REFUND_REQUIRED
}