package com.movie.notification_service.service;

import com.movie.notification_service.dto.BookingPaidEmailRequest;

public interface EmailService {
    void sendBookingPaidEmail(BookingPaidEmailRequest request);
}
