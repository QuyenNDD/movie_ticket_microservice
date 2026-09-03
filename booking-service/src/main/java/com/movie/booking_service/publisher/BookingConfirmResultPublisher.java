package com.movie.booking_service.publisher;

import com.movie.booking_service.dto.BookingConfirmResultEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BookingConfirmResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.booking-confirm-result-routing-key}")
    private String routingKey;

    public BookingConfirmResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishSuccess(String paymentId, String bookingId) {
        publish(BookingConfirmResultEvent.builder()
                .paymentId(paymentId)
                .bookingId(bookingId)
                .success(true)
                .build());
    }

    public void publishFailure(String paymentId, String bookingId, String errorMessage) {
        publish(BookingConfirmResultEvent.builder()
                .paymentId(paymentId)
                .bookingId(bookingId)
                .success(false)
                .errorMessage(errorMessage)
                .build());
    }

    private void publish(BookingConfirmResultEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);

        log.info("Published booking confirm result event. paymentId={}, bookingId={}, success={}",
                event.getPaymentId(), event.getBookingId(), event.isSuccess());
    }
}
