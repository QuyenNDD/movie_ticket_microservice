package com.movie.notification_service.service;

import com.movie.notification_service.dto.BookingPaidEmailRequest;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendBookingPaidEmail(BookingPaidEmailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(request.getToEmail());
            helper.setSubject("Xác nhận thanh toán vé xem phim thành công");
            helper.setText(buildHtmlContent(request), true);

            mailSender.send(message);

            System.out.println(">>> Đã gửi email thanh toán thành công tới: " + request.getToEmail());

        } catch (Exception e) {
            throw new RuntimeException("Gửi email xác nhận thanh toán thất bại: " + e.getMessage(), e);
        }
    }

    private String buildHtmlContent(BookingPaidEmailRequest request) {
        String amountText = NumberFormat
                .getCurrencyInstance(new Locale("vi", "VN"))
                .format(request.getAmount());

        String paidAt = request.getPaidAt() == null ? "" : request.getPaidAt();

        String seatHtml = buildSeatHtml(request);
        String snackHtml = buildSnackHtml(request);

        return """
            <div style="font-family: Arial, sans-serif; line-height: 1.6;">
                <h2>Thanh toán vé xem phim thành công</h2>

                <p>Xin chào,</p>

                <p>Hệ thống đã ghi nhận thanh toán thành công cho đơn đặt vé của bạn.</p>

                <table style="border-collapse: collapse; margin-top: 12px;">
                    <tr>
                        <td style="padding: 6px 12px; font-weight: bold;">Mã booking:</td>
                        <td style="padding: 6px 12px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 12px; font-weight: bold;">Tổng tiền:</td>
                        <td style="padding: 6px 12px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 6px 12px; font-weight: bold;">Thời gian thanh toán:</td>
                        <td style="padding: 6px 12px;">%s</td>
                    </tr>
                </table>

                <h3>Ghế đã đặt</h3>
                %s

                <h3>Bắp nước đã đặt</h3>
                %s

                <p>Vui lòng đến rạp đúng giờ và cung cấp mã booking khi cần đối soát.</p>

                <p>Cảm ơn bạn đã sử dụng dịch vụ.</p>
            </div>
            """.formatted(
                request.getBookingId(),
                amountText,
                paidAt,
                seatHtml,
                snackHtml
        );
    }

    private String buildSeatHtml(BookingPaidEmailRequest request) {
        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            return "<p>Không có thông tin ghế.</p>";
        }

        StringBuilder html = new StringBuilder();

        html.append("""
            <table style="border-collapse: collapse; width: 100%;">
                <tr>
                    <th style="border: 1px solid #ddd; padding: 8px;">Ghế</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Giá</th>
                </tr>
            """);

        for (BookingPaidEmailRequest.SeatItem seat : request.getSeats()) {
            String seatName = seat.getSeatName() != null && !seat.getSeatName().isBlank()
                    ? seat.getSeatName()
                    : seat.getSeatId();

            String priceText = seat.getPrice() == null
                    ? ""
                    : NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(seat.getPrice());

            html.append("""
                <tr>
                    <td style="border: 1px solid #ddd; padding: 8px;">%s</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">%s</td>
                </tr>
                """.formatted(seatName, priceText));
        }

        html.append("</table>");

        return html.toString();
    }

    private String buildSnackHtml(BookingPaidEmailRequest request) {
        if (request.getSnacks() == null || request.getSnacks().isEmpty()) {
            return "<p>Không có bắp nước.</p>";
        }

        StringBuilder html = new StringBuilder();

        html.append("""
            <table style="border-collapse: collapse; width: 100%;">
                <tr>
                    <th style="border: 1px solid #ddd; padding: 8px;">Tên món</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Số lượng</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Đơn giá</th>
                    <th style="border: 1px solid #ddd; padding: 8px;">Thành tiền</th>
                </tr>
            """);

        for (BookingPaidEmailRequest.SnackItem snack : request.getSnacks()) {
            String snackName = snack.getSnackName() != null && !snack.getSnackName().isBlank()
                    ? snack.getSnackName()
                    : snack.getSnackId();

            int quantity = snack.getQuantity() == null ? 0 : snack.getQuantity();
            long price = snack.getPrice() == null ? 0L : snack.getPrice();
            long total = price * quantity;

            String priceText = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(price);
            String totalText = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(total);

            html.append("""
                <tr>
                    <td style="border: 1px solid #ddd; padding: 8px;">%s</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">%d</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">%s</td>
                    <td style="border: 1px solid #ddd; padding: 8px;">%s</td>
                </tr>
                """.formatted(snackName, quantity, priceText, totalText));
        }

        html.append("</table>");

        return html.toString();
    }
}