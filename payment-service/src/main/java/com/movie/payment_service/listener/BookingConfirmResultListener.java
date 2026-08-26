package com.movie.payment_service.listener;

import com.movie.payment_service.dto.BookingConfirmResultEvent;
import com.movie.payment_service.service.MomoServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class BookingConfirmResultListener {

    private final MomoServiceImpl momoService;

    public BookingConfirmResultListener(MomoServiceImpl momoService) {
        this.momoService = momoService;
    }

    @RabbitListener(queues = "${app.rabbitmq.booking-confirm-result-queue}")
    public void handleBookingConfirmResult(BookingConfirmResultEvent event) {
        try {
            System.out.println(">>> Nhận kết quả confirm booking. bookingId=" + event.getBookingId()
                    + ", success=" + event.isSuccess());

            momoService.handleBookingConfirmResult(event);

        } catch (Exception ex) {
            System.err.println(">>> Xử lý kết quả confirm booking thất bại, RabbitMQ sẽ retry. bookingId="
                    + event.getBookingId()
                    + ", error="
                    + ex.getMessage());

            throw ex;
        }
    }
}
