package com.movie.notification_service.listener;

import com.movie.notification_service.dto.BookingPaidEmailEvent;
import com.movie.notification_service.service.EmailService;
import com.movie.notification_service.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class BookingPaidEmailListener {

    private final EmailService emailService;
    private final NotificationService notificationService;

    public BookingPaidEmailListener(EmailService emailService, NotificationService notificationService) {
        this.emailService = emailService;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.rabbitmq.booking-paid-queue}")
    public void handleBookingPaidEmailEvent(BookingPaidEmailEvent event) {
        try {
            System.out.println(">>> Received booking paid email event. bookingId="
                    + event.getBookingId());

            emailService.sendBookingPaidEmail(event);
            createInAppNotification(event);

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

    // Best-effort: lỗi tạo thông báo in-app không được làm retry gửi lại email đã gửi thành công
    private void createInAppNotification(BookingPaidEmailEvent event) {
        if (event.getUserId() == null || event.getUserId().isBlank()) {
            return;
        }

        try {
            String title = "Thanh toán vé thành công";
            String content = "Bạn đã thanh toán thành công cho hóa đơn " + event.getBookingId()
                    + " với số tiền " + event.getAmount() + "đ.";

            notificationService.createNotification(event.getUserId(), title, content, "BOOKING_PAID");

        } catch (Exception ex) {
            System.err.println(">>> Tạo thông báo in-app thất bại (không ảnh hưởng việc gửi mail). bookingId="
                    + event.getBookingId()
                    + ", error="
                    + ex.getMessage());
        }
    }
}