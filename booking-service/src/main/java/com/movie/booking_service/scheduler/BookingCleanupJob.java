package com.movie.booking_service.scheduler;

import com.movie.booking_service.entity.Booking;
import com.movie.booking_service.repository.BookingRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BookingCleanupJob {

    @Autowired
    private BookingRepository bookingRepository;

    // @Scheduled(fixedRate = 60000) nghĩa là: Cứ cách đúng 60.000 ms (1 phút) sẽ chạy hàm này 1 lần
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredBookings() {
        // Mốc thời gian: Những gì xảy ra trước 5 phút tính từ bây giờ
        LocalDateTime expirationThreshold = LocalDateTime.now().minusMinutes(5);

        // Tìm hóa đơn PENDING có bookingTime cũ hơn 5 phút
        List<Booking> expiredBookings = bookingRepository.findExpiredBookings(expirationThreshold);

        if (!expiredBookings.isEmpty()) {
            for (Booking booking : expiredBookings) {
                booking.setStatus("CANCELLED");
            }

            bookingRepository.saveAll(expiredBookings);

            // Log này sẽ xuất hiện trong tab Console của IntelliJ/Eclipse
            System.out.println(">>> [HỆ THỐNG] Đã hủy " + expiredBookings.size() +
                    " hóa đơn quá hạn thanh toán (dựa trên bookingTime).");
        }
    }
}
