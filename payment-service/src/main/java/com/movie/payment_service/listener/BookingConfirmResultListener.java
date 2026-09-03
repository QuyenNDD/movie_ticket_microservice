package com.movie.payment_service.listener;

import com.movie.payment_service.dto.BookingConfirmResultEvent;
import com.movie.payment_service.service.MomoServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BookingConfirmResultListener {

    private final MomoServiceImpl momoService;

    public BookingConfirmResultListener(MomoServiceImpl momoService) {
        this.momoService = momoService;
    }

    @RabbitListener(queues = "${app.rabbitmq.booking-confirm-result-queue}")
    public void handleBookingConfirmResult(BookingConfirmResultEvent event) {
        try {
            log.info("Nhận kết quả confirm booking. bookingId={}, success={}",
                    event.getBookingId(), event.isSuccess());

            momoService.handleBookingConfirmResult(event);

        } catch (Exception ex) {
            log.error("Xử lý kết quả confirm booking thất bại, RabbitMQ sẽ retry. bookingId={}, error={}",
                    event.getBookingId(), ex.getMessage());

            throw ex;
        }
    }
}
