package com.movie.notification_service.service;

import com.movie.notification_service.dto.BookingPaidEmailEvent;
import com.movie.notification_service.dto.BookingPaidEmailRequest;
import com.movie.notification_service.dto.PasswordResetEmailRequest;

public interface EmailService {
    void sendBookingPaidEmail(BookingPaidEmailRequest request);
    void sendBookingPaidEmail(BookingPaidEmailEvent event);
    void sendPasswordResetEmail(PasswordResetEmailRequest request);
}
