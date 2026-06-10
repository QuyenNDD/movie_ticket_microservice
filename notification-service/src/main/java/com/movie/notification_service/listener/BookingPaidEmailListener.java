package com.movie.notification_service.listener;

import com.movie.notification_service.dto.BookingPaidEmailEvent;
import com.movie.notification_service.service.EmailService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class BookingPaidEmailListener {

    private final EmailService emailService;

    public BookingPaidEmailListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RabbitListener(queues = "${app.rabbitmq.booking-paid-queue}")
    public void handleBookingPaidEmailEvent(BookingPaidEmailEvent event) {
        try {
            System.out.println(">>> Received booking paid email event. bookingId="
                    + event.getBookingId());

            emailService.sendBookingPaidEmail(event);

            System.out.println(">>> Email event processed successfully. bookingId="
                    + event.getBookingId());

        } catch (Exception ex) {
            System.err.println(">>> Gửi mail thất bại, RabbitMQ sẽ retry. bookingId="
                    + event.getBookingId()
                    + ", error="
                    + ex.getMessage());

            // Bắt buộc throw lại để RabbitMQ biết xử lý thất bại và retry
            throw ex;
        }
    }
}