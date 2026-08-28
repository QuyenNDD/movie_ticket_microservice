# Tóm tắt công việc — 2026-08-28

> Ghi lại nội dung đã trao đổi/thực hiện trong phiên làm việc này với Claude Code cho dự án `movie_ticket_microservice`. Tiếp nối `SESSION_SUMMARY_2026-08-26.md` (phiên 3).

## 1. Kiểm chứng checklist so với source code thật

Đầu phiên, chạy 5 agent song song đọc trực tiếp source code từng service, đối chiếu với các mục ✅ trong `FEATURE_CHECKLIST.md` (không suy đoán từ mô tả). Kết quả: tất cả mục ✅ đều khớp thật với code, chỉ có **1 điểm PARTIAL**:

- **Xóa mềm phim (catalog-service)**: `deleteMovie` chỉ đổi `status="STOPPED"` nhưng `getAllMovies`/`getMovieByTitle` (danh sách công khai + tìm kiếm) không lọc theo status — phim đã "xóa" vẫn lộ ra ở danh sách chung. Đã sửa ngay trong phiên (mục 2 bên dưới).

Phát hiện phụ (không sửa, chỉ ghi nhận): `BOOKING_SERVICE_BASE_URL`/tương tự bị hardcode ở vài nơi thay vì đọc env; log dùng `System.out/err.println` rải rác thay SLF4J; 2 route trùng lặp trong `api-gateway/application.yaml`; `RouteValidator` có vẻ không còn dùng; so sánh `X-Internal-Secret` bằng `.equals` thường (không constant-time).

## 2. Sửa bug xóa mềm phim (catalog-service)

Commit `9cd5401`. Thêm `findByStatusNot`/`findByTitleContainingIgnoreCaseAndStatusNot` để loại `STOPPED` khỏi `getAllMovies`/`getMovieByTitle`; vẫn tra cứu được qua `/movies/status/STOPPED` hoặc `/movies/{id}` cho admin.

## 3. Tính năng mới hoàn thành (Giai đoạn 1 — MVP lõi, checklist tăng từ 30/40 → 39/40)

Toàn bộ nhóm **1.3 Đặt vé & giữ chỗ** đã hoàn tất 100%. Nhóm **1.4 Thanh toán** và **1.5 Thông báo** gần như xong, chỉ còn "push notification" cần hạ tầng thật.

### 3.1 booking-service
- **Xem lịch sử đặt vé của tôi** (`349c22f`): `GET /api/v1/booking/my-bookings`, không cần entity mới, tái dùng `Booking.userId`.
- **Hủy vé đã thanh toán + hoàn tiền** (`e7955af`): `PUT /{bookingId}/cancel` giờ cho hủy cả booking PAID (trước chỉ hủy được PENDING) nếu suất chiếu chưa bắt đầu. Thêm field `Booking.cancellationReason`, `Booking.refundStatus`.
- **Vé điện tử có mã QR** (`afa5c66`): entity `Ticket` mới (bookingSeatId, qrCode, checkedInAt, checkedInBy). Vé sinh tự động 1 vé/ghế khi `confirmPayment` chuyển PAID lần đầu (không sinh trùng khi confirm idempotent). API `GET /{bookingId}/tickets`.
- **Check-in tại rạp** (`6583b93`): `POST /api/v1/booking/tickets/checkin` (chỉ ADMIN, chặn ở gateway), dùng chung entity `Ticket`. Chặn soát vé lặp lại, chặn vé thuộc booking đã hủy sau thanh toán.
- **Chọn combo bắp nước** (`ed9cde9`): entity `BookingCombo` mới, `BookingRequestDTO` nhận thêm `combos[{comboId,quantity}]` tương tự snack lẻ.
- **Nhắc lịch trước giờ chiếu** (`6c94d1c`): field `Booking.reminderSent` + scheduler `ShowtimeReminderJob` (mỗi 5 phút), gom theo `showtimeId` để giảm gọi catalog-service, nhắc khi suất chiếu còn ≤60 phút (`app.reminder.before-minutes`).
- Phát hiện môi trường: chạy booking-service ngoài Docker trên máy này, `CATALOG_SERVICE_URL` mặc định trỏ `host.docker.internal` DNS resolve ra IP LAN của máy nhưng bị Windows Firewall chặn — phải override tạm bằng `localhost` để test, không sửa `.env` gốc.

### 3.2 catalog-service
- **Chọn combo bắp nước** (`ed9cde9`, cùng commit với booking-service): entity `SnackCombo` + `SnackComboItem` (name, price, items[snackId,quantity]). CRUD tại `/api/v1/catalog/snack-combos` (ADMIN), validate giá combo phải thấp hơn tổng giá lẻ các món. Endpoint nội bộ `GET /snack-combos/{id}/price` cho booking-service.

### 3.3 payment-service
- **Lịch sử giao dịch thanh toán** (`6e35c30`): `GET /api/v1/payment/momo/my-transactions`, không cần entity mới, tái dùng `PaymentTransaction.userId`.
- **Hoàn tiền tự động qua MoMo** (`8a3865e`): entity `RefundTransaction` mới (paymentTransactionId, bookingId, amount, reason, status, momoRefundTransId, refundedAt). API nội bộ `POST /momo/refund/{bookingId}` (X-Internal-Secret), gọi thật API hoàn tiền MoMo (HMAC signature riêng cho refund, khác signature tạo thanh toán). Idempotent nếu đã hoàn thành công trước đó. booking-service tự động gọi API này ngay khi hủy booking PAID.

### 3.4 notification-service
- **Thông báo trong ứng dụng** (`c2d9668`): notification-service **lần đầu có database** — thêm `spring-boot-starter-data-jpa` + `mysql-connector-j` vào pom.xml, schema `notification_db` mới. Entity `Notification` (userId, title, content, type, isRead, createdAt). API cho user qua gateway (route mới `/api/v1/notifications/**`): `GET /my-notifications`, `GET /unread-count`, `PATCH /{id}/read`. API nội bộ `POST /internal/create`. Nối vào sự kiện thanh toán thành công có sẵn (`BookingPaidEmailEvent` qua RabbitMQ, thêm field `userId`): khi gửi mail xác nhận vé thì tạo luôn thông báo in-app tương ứng — lỗi tạo thông báo không làm retry gửi lại mail.
- Phát hiện phụ tiện tay sửa: notification-service thiếu `GlobalExceptionHandler` (lỗi nghiệp vụ trả về 500 mặc định thay vì 400 rõ ràng) — đã bổ sung.

## 4. Bỏ qua có chủ đích (đã hỏi và được xác nhận)

- **Thêm phương thức thanh toán khác (VNPay, ZaloPay, thẻ ngân hàng)**: cần merchant credential thật (TMN code/hash secret, app ID/key) mà chỉ user mới tạo được — giống tình huống OAuth Google/Facebook đã bỏ qua ở phiên trước.
- **Push notification (mobile/web push)**: cần hạ tầng web push thật (VAPID key, service worker phía FE) mà repo chưa có frontend.

## 5. Quy trình làm việc trong phiên

Mỗi tính năng đều: code → compile kiểm tra → khởi động lại service local → test end-to-end thật bằng curl (tạo dữ liệu QA, xác minh cả đường thành công lẫn các nhánh lỗi/bảo mật) → dọn dữ liệu test khỏi DB → tick `FEATURE_CHECKLIST.md` → commit riêng từng tính năng → hỏi xác nhận trước khi push. Đã push toàn bộ 10 commit lên `origin/main`.

## Commit đã push lên `origin/main` (thứ tự thời gian)
1. `349c22f` feat: xem lịch sử đặt vé của tôi
2. `9cd5401` fix: phim xóa mềm vẫn hiện trong danh sách/tìm kiếm chung
3. `e7955af` feat: hủy vé đã thanh toán + đánh dấu trạng thái hoàn tiền
4. `afa5c66` feat: vé điện tử có mã QR
5. `6583b93` feat: check-in tại rạp bằng quét mã QR
6. `ed9cde9` feat: chọn combo bắp nước theo gói ưu đãi
7. `6e35c30` feat: lịch sử giao dịch thanh toán cho người dùng
8. `8a3865e` feat: hoàn tiền tự động qua MoMo khi hủy vé đã thanh toán
9. `c2d9668` feat: thông báo trong ứng dụng (in-app notification)
10. `6c94d1c` feat: nhắc lịch trước giờ chiếu

## File đã tạo/cập nhật trong phiên này (theo service)
- Root: `.env` (thêm `NOTIFICATION_DB_URL/USERNAME/PASSWORD`), `.env.example` (đồng bộ, dùng placeholder), `FEATURE_CHECKLIST.md`, `SESSION_SUMMARY_2026-08-28.md` (file này).
- `api-gateway`: `application.yaml` (route `/api/v1/notifications/**` mới), `AuthenticationFilter.java` (thêm `/api/v1/booking/tickets/checkin`, `/api/v1/catalog/snack-combos` vào danh sách admin-only).
- `booking-service`: entity `Ticket`, `BookingCombo`; field mới trên `Booking` (`cancellationReason`, `refundStatus`, `reminderSent`); scheduler `ShowtimeReminderJob`; DTO `BookingSummaryDTO`, `CancelBookingRequestDTO`, `TicketResponseDTO`, `CheckInRequestDTO`; mở rộng `BookingController`/`BookingService`/`BookingServiceImpl`/`BookingRequestDTO`/`BookingResponseDTO`/`BookingRepository`; `application.yml` (thêm `payment-service-url`, `notification-service-url`, `app.reminder.before-minutes`).
- `catalog-service`: entity `SnackCombo`, `SnackComboItem`; repository/service/controller tương ứng; `MovieRepository`/`MovieServiceImpl` (fix xóa mềm); `CatalogController`/`CatalogService`/`CatalogServiceImpl` (endpoint giá combo nội bộ); `ServiceAccessFilter`/`InternalApiFilter` (path combo); `AppConstants`.
- `payment-service`: entity `RefundStatus`, `RefundTransaction`; repository/DTO tương ứng; `MomoService`/`MomoServiceImpl` (refund + lịch sử giao dịch); `PaymentController`; `ServiceAccessFilter` (path refund nội bộ); `application.yaml` (`momo.refund-endpoint`); `BookingPaidEmailEvent` (thêm `userId`).
- `notification-service`: `pom.xml` (thêm JPA + MySQL); entity `Notification`; repository/service/DTO/`GlobalExceptionHandler` mới; `NotificationController`, `InternalAccessFilter` (mở rộng), `BookingPaidEmailListener`/`BookingPaidEmailEvent` (tạo thông báo in-app); `application.yaml` (datasource, gateway-secret).

## Trạng thái môi trường cuối phiên (đang chạy trên máy dev)
- Docker: `mysql-db`, `redis-cache`, `rabbitmq-broker` đang chạy. `api-gateway` container **không** chạy phiên này (test trực tiếp từng service bằng header `X-Gateway-Secret`/`X-Internal-Secret`, không qua gateway thật).
- Service backend đang chạy local: auth-service (8083), catalog-service (8081), booking-service (8082), payment-service (8084), notification-service (8085).
- Dữ liệu test đã dọn sạch khỏi DB sau mỗi tính năng (không tích lũy rác như các phiên trước).

## Việc tiếp theo (chưa thực hiện)
- Giai đoạn 1 chỉ còn: đăng nhập Google/Facebook, thêm phương thức thanh toán khác, push notification — cả 3 đều cần credential/hạ tầng thật từ bên ngoài, đã bỏ qua có chủ đích.
- Giai đoạn 2 (kinh doanh & tăng trưởng), Giai đoạn 3 (quản trị & vận hành rạp) chưa bắt đầu.
- Giai đoạn 4 (nền tảng kỹ thuật): Swagger/OpenAPI, Actuator health check, Resilience4j, unit/integration test, CI/CD, container hóa đầy đủ 6 service, logging/metrics tập trung — vẫn chưa làm, đã đề cập nhiều phiên nhưng chưa bắt đầu.
- Dọn 2 route trùng lặp (`catalog-service-protected-write` / `catalog-service-user-protected`) trong `api-gateway/application.yaml` — phát hiện ở bước kiểm chứng đầu phiên, chưa sửa.
