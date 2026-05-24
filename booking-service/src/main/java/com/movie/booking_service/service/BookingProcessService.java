package com.movie.booking_service.service;

import com.movie.booking_service.entity.Booking;
import com.movie.booking_service.message.TicketEmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BookingProcessService {

    private final RabbitTemplate rabbitTemplate;

//    public void handleSuccessfulPayment(Booking booking, User user) {
//        // 1. Cập nhật trạng thái PAID vào DB
//        // ...
//
//        // 2. Tạo gói tin nhắn
//        TicketEmailMessage message = new TicketEmailMessage(
//                user.getEmail(),
//                booking.getId(),
//                booking.getShowtime().getMovie().getTitle(),
//                "A1, A2", // Logic gom tên ghế
//                booking.getShowtime().getRoom().getCinema().getName(),
//                booking.getShowtime().getStartTime().toString()
//        );
//
//        // 3. Ném vào RabbitMQ
//        rabbitTemplate.convertAndSend(
//                "notification.exchange",
//                "ticket.email.send",
//                message
//        );
//
//        System.out.println("Đã ủy quyền gửi email cho RabbitMQ!");
//    }
}
