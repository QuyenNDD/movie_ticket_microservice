package com.movie.payment_service.publisher;

import com.movie.payment_service.dto.BookingConfirmRequestEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BookingConfirmRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.booking-confirm-request-routing-key}")
    private String routingKey;

    public BookingConfirmRequestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BookingConfirmRequestEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);

        System.out.println(">>> Published booking confirm request event. paymentId="
                + event.getPaymentId() + ", bookingId=" + event.getBookingId());
    }
}
