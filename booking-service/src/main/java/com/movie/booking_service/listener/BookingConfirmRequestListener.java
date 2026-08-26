package com.movie.booking_service.listener;

import com.movie.booking_service.dto.BookingConfirmRequestEvent;
import com.movie.booking_service.publisher.BookingConfirmResultPublisher;
import com.movie.booking_service.service.BookingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class BookingConfirmRequestListener {

    private final BookingService bookingService;
    private final BookingConfirmResultPublisher resultPublisher;

    public BookingConfirmRequestListener(BookingService bookingService, BookingConfirmResultPublisher resultPublisher) {
        this.bookingService = bookingService;
        this.resultPublisher = resultPublisher;
    }

    @RabbitListener(queues = "${app.rabbitmq.booking-confirm-request-queue}")
    public void handleBookingConfirmRequest(BookingConfirmRequestEvent event) {
        try {
            bookingService.confirmPayment(event.getUserId(), event.getBookingId());

            System.out.println(">>> Booking confirmed via RabbitMQ. bookingId=" + event.getBookingId());

            resultPublisher.publishSuccess(event.getPaymentId(), event.getBookingId());

        } catch (Exception ex) {
            System.err.println(">>> Confirm booking thất bại. bookingId=" + event.getBookingId()
                    + ", error=" + ex.getMessage());

            resultPublisher.publishFailure(event.getPaymentId(), event.getBookingId(), ex.getMessage());
        }
    }
}
