# Kịch bản test — confirmBooking chuyển sang RabbitMQ (2026-08-24)

> Test cho thay đổi: `payment-service` không còn gọi HTTP đồng bộ sang `booking-service` để confirm booking sau khi thanh toán MoMo, mà publish message qua RabbitMQ (`booking.confirm.request.queue`), `booking-service` xử lý rồi publish kết quả trả về (`payment.booking.confirm.result.queue`).

## Chuẩn bị

- Cần chạy: `booking-service` (8082), `payment-service` (8084), `catalog-service` (8081), `auth-service` (8083, để lấy email khi gửi mail), cùng hạ tầng MySQL, Redis, RabbitMQ.
- RabbitMQ management UI: http://localhost:15672 (mặc định `guest`/`guest`) — dùng để xem queue, số message, publish tay message giả lập.
- Endpoint test nhanh (không cần gọi MoMo thật): `POST http://localhost:8084/api/v1/payment/momo/test/{bookingId}/success`, header `X-User-Id: <userId>`. Endpoint này gọi `testConfirmSuccess`, đi qua đúng luồng `confirmBookingAndMarkSuccess` mới.
- Cần 1 booking đang ở trạng thái `PENDING` trước mỗi kịch bản: gọi `POST http://localhost:8082/api/v1/booking/hold` với header `x-user-id` để giữ ghế trước.
- Bảng cần theo dõi trong lúc test:
  - `payment_db.payment_transactions`: cột `status`, `retry_count`, `next_retry_at`, `last_error`, `email_sent`.
  - `booking_db.bookings`: cột `status`.
- Log cần để ý (console của từng service): các dòng bắt đầu bằng `>>> [ĐÃ GỬI]`, `>>> [THÀNH CÔNG]`, `>>> [CẦN RETRY]`, `>>> Booking confirmed via RabbitMQ`, `>>> Published booking confirm...`, `>>> Nhận kết quả confirm booking...`.

---

## 1. Happy path — không có lỗi gì

- [ ] Hold ghế tạo booking `PENDING`.
- [ ] Gọi endpoint test-success cho booking đó.
- [ ] Kỳ vọng:
  - Log payment-service: `[ĐÃ GỬI]` → sau đó `[THÀNH CÔNG]` xuất hiện trong vòng vài giây.
  - Log booking-service: `Booking confirmed via RabbitMQ`.
  - `bookings.status` = `PAID`.
  - `payment_transactions.status` = `SUCCESS`, `email_sent` = `1`.
  - Email vé được gửi tới đúng địa chỉ user (kiểm tra hộp thư hoặc log `notification-service`).
  - Trong RabbitMQ UI, 2 queue mới (`booking.confirm.request.queue`, `payment.booking.confirm.result.queue`) không còn message tồn đọng sau khi xử lý xong.

## 2. booking-service down lúc payment-service publish request

- [ ] Hold ghế tạo booking `PENDING`.
- [ ] Tắt `booking-service`.
- [ ] Gọi endpoint test-success.
- [ ] Kỳ vọng ngay sau khi gọi:
  - `payment_transactions.status` = `CONFIRM_PENDING`, `retry_count` = 1, `next_retry_at` có giá trị (~1 phút sau).
  - RabbitMQ UI: `booking.confirm.request.queue` có 1 message đang chờ (Ready).
- [ ] Bật lại `booking-service`.
- [ ] Kỳ vọng: message trong queue được tiêu thụ ngay khi service khởi động lại (không cần đợi retry job), booking chuyển `PAID`, result event được publish, payment-service nhận và set `SUCCESS`.

## 3. payment-service down lúc chờ nhận kết quả (test độ bền message)

- [ ] Hold ghế tạo booking `PENDING`.
- [ ] Tắt `payment-service`.
- [ ] Vào RabbitMQ UI, tab **Exchanges** → `movie.ticket.exchange` → mục "Publish message", tự publish 1 message tới routing key `booking.confirm.request` với payload:
  ```json
  {"paymentId": "<lấy id thật từ bảng payment_transactions nếu có, hoặc tạo payment trước bằng bước tạo QR>", "bookingId": "<bookingId>", "userId": "<userId>"}
  ```
- [ ] Kỳ vọng: `booking-service` (đang chạy) tiêu thụ, confirm booking thành công, publish result vào `payment.booking.confirm.result.queue` — message này phải **tồn đọng chờ** vì `payment-service` đang tắt (kiểm tra queue Ready count = 1 trong RabbitMQ UI).
- [ ] Bật lại `payment-service`.
- [ ] Kỳ vọng: message được tiêu thụ ngay, payment tương ứng chuyển `SUCCESS` (nếu paymentId hợp lệ và tồn tại trong DB).

> Mục đích: xác nhận queue là `durable` và message không bị mất khi 1 trong 2 service tạm ngưng — đúng lý do chuyển sang RabbitMQ thay vì gọi HTTP trực tiếp.

## 4. Booking đã hết hạn / đã bị hủy khi confirm tới

- [ ] Hold ghế tạo booking `PENDING`, sau đó tự set `bookings.status = 'CANCELLED'` trực tiếp trong DB (giả lập đã hết hạn/hủy trước khi thanh toán kịp confirm).
- [ ] Gọi endpoint test-success cho booking đó.
- [ ] Kỳ vọng:
  - Log booking-service: confirm thất bại, publish failure result kèm lý do (`Hóa đơn này đã bị hủy...`).
  - Log payment-service: `[CẦN RETRY]` kèm `error` là lý do trên.
  - `payment_transactions.status` vẫn `CONFIRM_PENDING`, `last_error` chứa lý do thất bại, `next_retry_at` đã được đặt sẵn từ lúc gửi request (không đổi thêm ở bước này).
- [ ] (Tuỳ chọn, mất thời gian) Đợi đủ `app.payment-retry.max-retry-count` lần (mặc định 10, có thể set `PAYMENT_RETRY_MAX_COUNT=2` và `PAYMENT_RETRY_FIXED_DELAY_MS=10000` khi chạy test cho nhanh) để xác nhận payment cuối cùng chuyển sang `PAYMENT_REVIEW`.

## 5. Idempotent khi message bị gửi lại (duplicate delivery)

Test đúng phần đã sửa trong `BookingServiceImpl.confirmPayment` (không throw lỗi khi booking đã `PAID`).

- [ ] Thực hiện kịch bản 1 (happy path) cho tới khi `bookings.status = PAID`.
- [ ] Vào RabbitMQ UI, publish tay 1 message **giống hệt request cũ** (cùng `bookingId`, `paymentId`) vào routing key `booking.confirm.request`.
- [ ] Kỳ vọng:
  - `booking-service` không ném lỗi, trả về response `"Hóa đơn này đã được thanh toán trước đó."`, log vẫn ghi `Booking confirmed via RabbitMQ` (không có exception).
  - Publish `success=true` về lại `payment.booking.confirm.result.queue`.
  - `payment-service` nhận, do `payment.status` đã là `SUCCESS` sẵn nên `handleBookingConfirmResult` return sớm, **không** gửi lại email (kiểm tra log/hộp thư không có email thứ 2), `email_sent` vẫn `1`.

## 6. Kiểm tra không phá luồng gửi email vé cũ

RabbitMQConfig của payment-service bị sửa (thêm queue mới) — xác nhận queue email cũ (`notification.booking.paid.queue`) vẫn hoạt động bình thường.

- [ ] Chạy lại kịch bản 1 (happy path).
- [ ] Kỳ vọng: email vé vẫn được gửi như trước khi sửa code (không có gì thay đổi ở luồng này).

## 7. Kiểm tra topology RabbitMQ lúc khởi động

- [ ] Khởi động `payment-service` và `booking-service`.
- [ ] Vào RabbitMQ UI → **Exchanges** → `movie.ticket.exchange` (type `direct`).
- [ ] Kỳ vọng thấy đủ 3 queue bind vào exchange này với đúng routing key:
  - `notification.booking.paid.queue` ← `notification.booking.paid`
  - `booking.confirm.request.queue` ← `booking.confirm.request`
  - `payment.booking.confirm.result.queue` ← `payment.booking.confirm.result`

## 8. Chạy song song nhiều booking (không lẫn dữ liệu)

- [ ] Hold ghế, tạo 2 booking khác nhau (2 user hoặc 2 suất chiếu khác nhau).
- [ ] Gọi endpoint test-success gần như đồng thời cho cả 2 booking.
- [ ] Kỳ vọng: cả 2 đều chuyển `PAID`/`SUCCESS` đúng, không có trường hợp booking A bị confirm nhầm bằng kết quả của booking B (do `paymentId`/`bookingId` được gắn trong từng message).

---

## Ghi chú khi test xong

Theo quy tắc trong `CLAUDE.md`: nếu tất cả kịch bản trên pass, nhớ yêu cầu Claude cập nhật `SESSION_SUMMARY_<ngày>.md` với kết quả test. Việc chuyển `callBookingConfirm` sang RabbitMQ không nằm trong `FEATURE_CHECKLIST.md` (đây là cải thiện hạ tầng/chịu tải, không phải tính năng nghiệp vụ mới) nên không cần tick ô nào ở đó.
