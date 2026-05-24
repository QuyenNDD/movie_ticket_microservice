package com.movie.notification_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailListenerService {

//    private final EmailSender emailSender; // Class chứa logic gửi mail thực tế

//    @RabbitListener(queues = "ticket.email.queue")
//    public void consumeEmailMessage(TicketEmailMessage message) {
//        System.out.println("RabbitMQ nhận được yêu cầu gửi vé cho: " + message.getToEmail());
//
//        try {
//            // Chỗ này gọi hàm cấu hình JavaMailSender hoặc Thymeleaf để gửi mail
//            emailSender.sendTicket(message);
//        } catch (Exception e) {
//            System.err.println("Gửi mail thất bại, RabbitMQ có thể cấu hình gửi lại (Retry)!");
//            throw e; // Ném lỗi để RabbitMQ biết là chưa xử lý xong, yêu cầu gửi lại
//        }
//    }
}
