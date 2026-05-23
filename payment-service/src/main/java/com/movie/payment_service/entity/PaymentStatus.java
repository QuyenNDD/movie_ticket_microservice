package com.movie.payment_service.entity;

public enum PaymentStatus {
    INIT,
    CONFIRM_PENDING,
    SUCCESS,
    FAILED,
    EXPIRED
}