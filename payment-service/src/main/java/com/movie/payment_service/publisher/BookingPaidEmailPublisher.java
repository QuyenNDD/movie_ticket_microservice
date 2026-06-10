package com.movie.payment_service.publisher;

import com.movie.payment_service.dto.BookingPaidEmailEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BookingPaidEmailPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.booking-paid-routing-key}")
    private String routingKey;

    public BookingPaidEmailPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BookingPaidEmailEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);

        System.out.println(">>> Published booking paid email event. bookingId="
                + event.getBookingId());
    }
}