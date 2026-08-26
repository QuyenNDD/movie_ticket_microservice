# Tóm tắt công việc — 2026-08-24

> Ghi lại nội dung đã trao đổi/thực hiện trong phiên làm việc này với Claude Code cho dự án `movie_ticket_microservice`.

## 1. Khảo sát cấu trúc microservice hiện tại
- Xác nhận dự án gồm **6 service**: `api-gateway`, `auth-service`, `booking-service`, `catalog-service`, `notification-service`, `payment-service`.
- `docker-compose.yml` hiện chỉ build/khai báo container cho `api-gateway` (+ hạ tầng `mysql`, `redis`, `rabbitmq`) — 5 service còn lại chưa có trong compose.

## 2. Khảo sát chức năng đã hoàn thành theo từng service
Rà soát toàn bộ Controller + Service Impl (loại bỏ stub/rỗng) của 6 service, xác nhận đã hoàn thiện:
- **api-gateway**: xác thực JWT tập trung, chèn header định danh, phân quyền admin cho route ghi dữ liệu, rate limiting.
- **auth-service**: đăng ký, đăng nhập (JWT access + refresh), refresh token, xem profile, API nội bộ lấy user theo ID.
- **catalog-service**: CRUD phim/rạp/phòng/ghế/suất chiếu/snack, quản lý loại ghế (kể cả ghế COUPLE), tính giá ghế/snack theo suất chiếu, upload ảnh Cloudinary.
- **booking-service**: xem sơ đồ ghế, giữ chỗ (Redis lock TTL), xử lý ghế đôi/ghế cô lập, xác nhận/hủy booking.
- **payment-service**: tạo thanh toán MoMo, xác thực IPN callback, retry xác nhận booking, publish sự kiện qua RabbitMQ.
- **notification-service**: gửi email xác nhận vé (đồng bộ + qua RabbitMQ listener).
- Phát hiện `BookingProcessService.java` (booking-service) là dead code — logic bị comment toàn bộ, không còn được gọi.

## 3. Đánh giá mức độ hoàn thiện & chức năng dang dở
- Xác định các mảng **chưa có endpoint nào**: lịch sử đặt vé, hủy vé đã thanh toán + hoàn tiền, đăng xuất, quên/đổi mật khẩu, quản lý user cho admin, review/watchlist phim, QR check-in, dashboard thống kê, voucher/khuyến mãi.
- Kết luận: luồng lõi kỹ thuật khó nhất (giữ ghế đồng thời, thanh toán MoMo thật) đã làm tốt và đủ cho mục đích đồ án/demo kỹ thuật; nhưng còn thiếu trải nghiệm sau đặt vé và công cụ vận hành cho rạp nếu muốn thành sản phẩm thương mại thật.

## 4. Roadmap nâng cấp lên sản phẩm thực tế
Đề xuất lộ trình theo giai đoạn: (0) vá rủi ro bảo mật ngay — thiếu `.gitignore`, rà soát secret trong `.env`; (1) hoàn thiện tính năng lõi còn thiếu; (2) circuit breaker/retry + service discovery giữa các service; (3) unit/integration test + CI/CD; (4) container hóa đầy đủ + logging/metrics tập trung; (5) tài liệu API (Swagger).

## 5. Tạo `FEATURE_CHECKLIST.md`
- File checklist đầy đủ tính năng của một sản phẩm đặt vé xem phim thực tế, chia 5 giai đoạn, đánh dấu ✅/⬜ theo đúng hiện trạng code.
- Kết quả: Giai đoạn 1 (MVP lõi) 24/40 mục, Giai đoạn 4 (nền tảng kỹ thuật) 3/17 mục, các Giai đoạn 2/3/5 (kinh doanh, quản trị, mở rộng) 0/5 mục mỗi giai đoạn.

## 6. Khảo sát cấu trúc database hiện tại
- Xác nhận mô hình **database per service**: `auth_db`, `catalog_db`, `booking_db`, `payment_db` (cùng 1 MySQL container, khác schema); `notification-service` không có DB.
- Liệt kê đầy đủ 11 entity hiện có và quan hệ giữa chúng (kể cả liên kết ngầm qua ID giữa các service khác nhau, không có FK thật).
- Phát hiện rủi ro: `ddl-auto: update` ở cả 4 service (chưa dùng Flyway/Liquibase), nhiều trường `status`/`role`/`seatType` là String tự do thay vì Enum thật, lỗi chính tả cột `seat_lable`.

## 7. Xác định entity DB cần bổ sung & cập nhật vào checklist
- Với từng tính năng còn thiếu trong `FEATURE_CHECKLIST.md`, xác định entity DB tương ứng cần tạo mới (ví dụ: `RefreshToken`, `PasswordResetToken`, `Review`, `Favorite`, `Ticket`, `RefundTransaction`, `Notification`...).
- Đã cập nhật trực tiếp các ghi chú `→ Entity cần thêm: ...` vào từng mục trong `FEATURE_CHECKLIST.md`, kèm bảng tổng hợp "Entity DB ưu tiên cần thêm (Giai đoạn 1)" ở cuối file.
- Lưu ý quan trọng: `notification-service` hiện chưa có database — cần khởi tạo schema `notification_db` mới nếu muốn làm tính năng thông báo trong app.

## File đã tạo/cập nhật trong phiên này
- `FEATURE_CHECKLIST.md` (tạo mới, sau đó cập nhật thêm ghi chú entity)
- `SESSION_SUMMARY_2026-08-24.md` (file này)

## Việc tiếp theo (chưa thực hiện — tính tới hết phiên 1)
- Chưa code entity/tính năng nào mới — toàn bộ phiên 1 là khảo sát + lập kế hoạch.
- Đang chờ quyết định: bắt đầu code entity ưu tiên nào trước (gợi ý: `RefreshToken`, `Ticket`, `Review`, `Favorite`, `Notification`).

---

# Phiên làm việc 2 (cùng ngày) — Chịu tải & bất đồng bộ

## 8. Khảo sát xử lý bất đồng bộ hiện tại
- Xác nhận RabbitMQ chỉ dùng ở 1 luồng duy nhất trước đó: payment-service → notification-service (gửi email vé), config bị khai báo trùng lặp ở 2 service.
- Không có `@Async`, `CompletableFuture`, Resilience4j/Hystrix, Spring Retry (`@Retryable`) ở đâu trong dự án.
- Phát hiện rủi ro chịu tải rõ nhất: `RestTemplate`/Feign gọi giữa các service **không có timeout** — 1 service chậm có thể kéo treo thread ở service gọi.

## 9. Sửa timeout cho RestTemplate/Feign
- Thêm bean `RestTemplate` có connect/read timeout (mặc định 3s/5s, cấu hình qua env `REST_CLIENT_CONNECT_TIMEOUT_MS`/`REST_CLIENT_READ_TIMEOUT_MS`) cho `catalog-service` (mới), `payment-service` (mới, `RestTemplateConfig.java`), `booking-service` (thêm vào bean có sẵn trong `ModelMapperConfig.java`).
- Thay toàn bộ `new RestTemplate()` inline (1 chỗ ở catalog-service, 4 chỗ ở `MomoServiceImpl` payment-service) bằng bean inject.
- Thêm timeout cho Feign client `CatalogClient` (booking-service) qua `feign.client.config.default.connect-timeout/read-timeout` trong `application.yml`.

## 10. Chuyển `callBookingConfirm` (payment-service → booking-service) sang RabbitMQ
- Quyết định thiết kế (đã hỏi và được chọn): **Request + Reply qua 2 queue**, không dùng fire-and-forget, để giữ nguyên tính đúng đắn cũ — payment chỉ chuyển `SUCCESS` sau khi thực sự nhận được xác nhận từ booking-service.
- Luồng mới: payment-service publish `BookingConfirmRequestEvent` (paymentId, bookingId, userId) → booking-service lắng nghe, gọi `confirmPayment()` nội bộ, publish lại `BookingConfirmResultEvent` (success/errorMessage) → payment-service lắng nghe, chỉ set `SUCCESS` khi `success=true`.
- Retry đổi từ "phản ứng khi lỗi" sang "chủ động lên lịch": `confirmBookingAndMarkSuccess` tăng `retryCount` + đặt `nextRetryAt` ngay lúc publish (vì không còn biết kết quả đồng bộ); `PaymentConfirmRetryJob` (có sẵn) vẫn là lưới an toàn cuối khi mất message/booking-service down.
- **Sửa 1 lỗ hổng đúng đắn bắt buộc phải sửa để async an toàn**: `BookingServiceImpl.confirmPayment()` trước đây ném lỗi nếu booking đã `PAID`; nay trả về thành công (idempotent) — cần thiết vì RabbitMQ có thể gửi lại message (at-least-once delivery).
- File mới: `BookingConfirmRequestEvent`, `BookingConfirmResultEvent` (DTO ở cả 2 service), `BookingConfirmRequestPublisher` (payment), `BookingConfirmResultPublisher` (booking), `BookingConfirmRequestListener` (booking), `BookingConfirmResultListener` (payment), `RabbitMQConfig` mới cho booking-service (trước đó có dependency `spring-boot-starter-amqp` sẵn nhưng chưa dùng).

## 11. Tạo file kịch bản test & chạy test end-to-end
- Tạo `TEST_SCENARIOS_BOOKING_CONFIRM.md`: 8 kịch bản (happy path, service down giữa chừng, message durability, booking hết hạn/hủy, idempotent khi gửi lại message, không phá luồng email cũ, topology RabbitMQ, chạy song song nhiều booking).
- User báo MoMo sandbox lỗi không quét QR test được → chỉ ra endpoint có sẵn `POST /api/v1/payment/momo/test/{bookingId}/success` (giả lập thanh toán thành công, không cần MoMo thật, vẫn chạy qua đúng luồng RabbitMQ mới) kèm cách gọi qua gateway hoặc gọi thẳng payment-service (header `X-Gateway-Secret` + `X-User-Id`).
- Tự chạy test thật: khởi động Docker (mysql-db, redis-cache, rabbitmq-broker), tạo 4 schema DB, khởi động cả 5 service local, seed dữ liệu mẫu (user, phim, rạp, phòng, ghế, suất chiếu), rồi chạy các kịch bản.
- **Kết quả: PASS** — Kịch bản 1 (happy path), 5 (idempotent khi replay message), 7 (topology RabbitMQ) đều pass với bằng chứng cụ thể (DB, log, queue message count). Kịch bản 2 (durability) mới xác nhận được nửa đầu (message không mất khi service tắt).
- Phát hiện 2 bug có sẵn từ trước, không liên quan thay đổi RabbitMQ, **chưa sửa** (ngoài phạm vi phiên này):
  1. `payment-service`: default `app.auth-service-url` thiếu path `/api/v1/auth` → `getUserEmailFromAuth()` gọi sai URL, trả 401 nếu không override qua env `AUTH_SERVICE_URL`.
  2. `.env`: `MAIL_PASSWORD` (Gmail app password) bị Gmail từ chối (`535-5.7.8`) — pipeline RabbitMQ gửi đúng, chỉ bước gửi SMTP thật lỗi do credential sai/hết hạn.
  3. (phụ) `catalog-service` `MovieServiceImpl`: thông báo lỗi validate ngày phát hành bị ngược nghĩa (chỉ message gây hiểu lầm, logic chặn vẫn đúng).

## File đã tạo/cập nhật trong phiên 2
- Sửa: `catalog-service/config/ModelMapperConfig.java`, `catalog-service/service/RoomServiceImpl.java`, `booking-service/config/ModelMapperConfig.java`, `booking-service/service/BookingServiceImpl.java`, `booking-service/application.yml`, `payment-service/service/MomoServiceImpl.java`, `payment-service/config/RabbitMQConfig.java`, `payment-service/application.yaml`, `catalog-service/application.yaml`.
- Tạo mới: `payment-service/config/RestTemplateConfig.java`, `booking-service/config/RabbitMQConfig.java`, DTO/publisher/listener cho luồng confirm (liệt kê ở mục 10), `TEST_SCENARIOS_BOOKING_CONFIRM.md`, `CLAUDE.md` (quy tắc làm việc — đọc đầu phiên, tick checklist, tự cập nhật summary).

## Trạng thái môi trường cuối phiên (đang chạy trên máy dev)
- Docker: `mysql-db` (3307), `redis-cache` (6379), `rabbitmq-broker` (5672/15672) đang chạy.
- Service local đang chạy nền: auth-service (8083), catalog-service (8081), payment-service (8084, đã restart với `AUTH_SERVICE_URL` đúng), notification-service (8085).
- **booking-service (8082) đang TẮT** — bị kill lúc test kịch bản 2 (durability), chưa khởi động lại. Có 1 message test giả (`bookingId=scenario2-test-booking`) đang chờ trong `booking.confirm.request.queue`, sẽ tự xử lý (fail vô hại) khi booking-service chạy lại.
- Dữ liệu test còn trong DB: user `qa-test-1787566636@example.com`, movie/cinema/room/showtime QA, 2 booking test.

## Việc tiếp theo (phiên 2)
- Bật lại `booking-service` nếu muốn hệ thống chạy đủ 5 service.
- Cân nhắc sửa 2 bug pre-existing phát hiện được (auth-service-url thiếu path, MAIL_PASSWORD sai) nếu muốn luồng gửi email thật hoạt động khi test thủ công tiếp.
- Kịch bản 2 (durability) còn thiếu nửa sau (xác nhận tự xử lý tiếp khi booking-service bật lại) — có thể test thủ công.
- Circuit breaker (Resilience4j) cho các lời gọi liên service quan trọng vẫn là bước tiếp theo trong roadmap chịu tải (đã đề cập ở phiên 1, mục 4).
