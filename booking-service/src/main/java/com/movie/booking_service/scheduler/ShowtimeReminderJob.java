package com.movie.booking_service.scheduler;

import com.movie.booking_service.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShowtimeReminderJob {

    @Autowired
    private BookingService bookingService;

    // Cứ 5 phút quét 1 lần các booking PAID chưa được nhắc lịch
    @Scheduled(fixedRate = 300000)
    public void remindUpcomingShowtimes() {
        bookingService.sendUpcomingShowtimeReminders();
    }
}
