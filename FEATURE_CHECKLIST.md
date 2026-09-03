# Checklist tính năng — Movie Ticket Platform

> Danh sách đầy đủ các tính năng cần có của một sản phẩm web/app đặt vé xem phim thực tế, chia theo giai đoạn.
> ✅ đã làm | ⬜ chưa làm — dựa trên khảo sát mã nguồn tại thời điểm 2026-08-24.

---

## Giai đoạn 1 — MVP lõi (luồng đặt vé cơ bản)

### 1.1 Xác thực & tài khoản
- [x] Đăng ký tài khoản
- [x] Đăng nhập (JWT access token + refresh token)
- [x] Làm mới access token
- [x] Xem thông tin cá nhân (`/me`)
- [x] Đăng xuất (thu hồi refresh token)
  - → Entity đã tạo: `RefreshToken` (userId, token, revoked, expiresAt) — auth_db
- [x] Quên mật khẩu / đặt lại mật khẩu qua email
  - → Entity đã tạo: `PasswordResetToken` (userId, token, used, expiresAt) — auth_db
- [x] Đổi mật khẩu
  - → không cần entity mới, dùng lại `User`
- [x] Xác minh email khi đăng ký
  - → Entity đã tạo: `EmailVerificationToken` (userId, token, expiresAt) — auth_db.
    Chỉ theo dõi trạng thái (`User.emailVerified`), **không chặn login** khi
    chưa xác minh — quyết định có chủ đích để không phá vỡ user cũ trong DB
    (mặc định `emailVerified=false` khi thêm cột). Có thể siết chặn sau khi
    backfill dữ liệu cũ nếu cần.
- [ ] Đăng nhập qua mạng xã hội (Google/Facebook)
  - → Entity cần thêm: `SocialAccount` (provider, providerUserId, userId) — auth_db

### 1.2 Danh mục phim & rạp
- [x] CRUD phim (thêm/sửa/xóa mềm/xem)
  - → xóa mềm (`status="STOPPED"`) đã được lọc khỏi `getAllMovies`/`getMovieByTitle`
    (dùng `findByStatusNot`/`findByTitleContainingIgnoreCaseAndStatusNot`); vẫn
    tra cứu được qua `/movies/status/STOPPED` hoặc `/movies/{id}` cho admin.
- [x] Tìm kiếm phim theo tên
- [x] Lọc phim theo trạng thái (đang chiếu/sắp chiếu)
- [x] Cập nhật poster, trailer phim
- [x] CRUD rạp chiếu, tìm kiếm theo tên/thành phố
- [x] CRUD phòng chiếu kèm sơ đồ ghế (grid layout)
- [x] Quản lý loại ghế: thường / VIP / đôi (couple) / bảo trì
- [x] CRUD suất chiếu
- [x] Xem lịch chiếu theo rạp + ngày, theo phim + ngày
- [x] CRUD snack (bắp nước), cập nhật ảnh
- [x] Đánh giá & bình luận phim (review, rating)
  - → Entity đã tạo: `Review` (movieId, userId, rating, comment, createdAt) — catalog_db.
    Mỗi user chỉ đánh giá 1 lần/phim (unique constraint). API tại
    `/api/v1/catalog/reviews` (không nằm dưới `/movies/**` để tránh bị
    gateway coi là API quản trị movie cần quyền ADMIN).
- [x] Danh sách yêu thích / watchlist
  - → Entity đã tạo: `Favorite` (userId, movieId, createdAt) — catalog_db, unique
    constraint (userId, movieId). API tại `/api/v1/catalog/favorites`; khác
    `/reviews` ở chỗ `GET` cũng cần đăng nhập (danh sách riêng của user, không
    public) — đã thêm route `GET` yêu cầu auth tương ứng ở api-gateway.
- [ ] Gợi ý phim liên quan / phổ biến / sắp ra mắt
  - → không cần entity mới, suy ra từ `Movie`/`Review` hiện có (hoặc `ViewHistory` nếu cá nhân hóa — xem Giai đoạn 5)

### 1.3 Đặt vé & giữ chỗ
- [x] Xem sơ đồ ghế theo suất chiếu (trạng thái available/locked/paid)
- [x] Giữ chỗ tạm thời (hold), khóa bằng Redis TTL chống trùng ghế
- [x] Chọn ghế đôi (couple = 2 chỗ ngồi, 1 mã ghế)
- [x] Chặn ghế trống bị cô lập, giới hạn số ghế tối đa/đơn
- [x] Tính tiền ghế + đồ ăn/nước
- [x] Xác nhận thanh toán / hủy đặt vé (khi còn PENDING)
- [x] Xem chi tiết 1 booking (kèm thời gian còn lại để thanh toán)
- [x] Xem lịch sử đặt vé của tôi (danh sách booking theo user)
  - → không cần entity mới, `Booking.userId` đã đủ dữ liệu để query. API tại
    `GET /api/v1/booking/my-bookings`, sắp xếp mới nhất trước, tái dùng logic
    tính `expiresInSeconds` từ `getBookingDetails` (chỉ khác 0 khi PENDING).
- [x] Hủy vé đã thanh toán + hoàn tiền
  - → đã bổ sung field `cancellationReason`, `refundStatus` vào `Booking` — booking_db.
    `PUT /{bookingId}/cancel` giờ nhận thêm body `{reason}` (tùy chọn) và cho phép
    hủy cả booking đang PAID (trước đây chỉ hủy được PENDING), với điều kiện suất
    chiếu chưa bắt đầu. `refundStatus` = `NOT_APPLICABLE` (hủy khi còn PENDING,
    chưa thu tiền) hoặc `PENDING` (đã thu tiền, chờ hoàn). **Hoàn tiền thực tế
    (gọi MoMo refund) chưa làm** — xem `RefundTransaction` ở mục Thanh toán (1.4).
- [x] Vé điện tử có mã QR
  - → Entity đã tạo: `Ticket` (bookingSeatId, qrCode, checkedInAt, checkedInBy) — booking_db,
    unique theo `bookingSeatId` và `qrCode`. Vé được sinh tự động (1 vé/ghế) ngay khi
    `confirmPayment` chuyển booking sang PAID lần đầu (không sinh trùng khi confirm
    idempotent). API xem vé: `GET /api/v1/booking/{bookingId}/tickets` (chỉ chủ booking,
    chỉ khi đã PAID). `qrCode` là chuỗi token ngẫu nhiên — render thành ảnh QR là việc
    của FE.
- [x] Check-in tại rạp (quét QR soát vé)
  - → dùng chung entity `Ticket` ở trên. API `POST /api/v1/booking/tickets/checkin`
    (body `{qrCode}`), chỉ nhân viên có quyền ADMIN được gọi (gateway chặn ở
    `isAdminEndpoint`). Chặn soát vé lặp lại (đã có `checkedInAt`), chặn soát vé
    thuộc booking đã bị hủy sau khi thanh toán.
- [x] Chọn combo bắp nước theo gói ưu đãi
  - → Entity đã tạo: `SnackCombo` + `SnackComboItem` (name, price, items[snackId,quantity]) —
    catalog_db. CRUD tại `/api/v1/catalog/snack-combos` (ADMIN), validate giá combo phải
    thấp hơn tổng giá lẻ các món (đảm bảo đúng nghĩa "ưu đãi"). booking-service: entity
    `BookingCombo` mới (booking_db), `BookingRequestDTO` nhận thêm `combos[{comboId,quantity}]`
    tương tự snack lẻ, cộng dồn vào `totalPrice` qua lookup giá nội bộ tại catalog-service.

### 1.4 Thanh toán
- [x] Thanh toán qua MoMo (tạo giao dịch, lấy QR/link thanh toán)
- [x] Xác thực callback IPN từ MoMo (chống giả mạo chữ ký, chống xử lý lặp)
- [x] API test giả lập thanh toán thành công (môi trường dev)
- [ ] Thêm phương thức thanh toán khác (VNPay, ZaloPay, thẻ ngân hàng)
  - → tổng quát hóa field `provider`/`method` trong `PaymentTransaction` — payment_db
- [x] Hoàn tiền tự động khi hủy vé
  - → Entity đã tạo: `RefundTransaction` (paymentTransactionId, bookingId, amount, reason, status,
    momoRefundTransId, refundedAt) — payment_db. API nội bộ `POST /api/v1/payment/momo/refund/{bookingId}`
    (X-Internal-Secret), gọi thật API hoàn tiền MoMo (HMAC signature riêng cho refund). booking-service
    tự động gọi API này ngay khi hủy 1 booking PAID (`cancelBooking`), cập nhật `refundStatus`:
    `COMPLETED` (MoMo xác nhận hoàn thành công) / `FAILED` (MoMo từ chối, hoặc giao dịch test không có
    transId thật) / giữ `PENDING` nếu không gọi được payment-service (network lỗi — không chặn việc hủy
    vé, cần xử lý thủ công sau). Idempotent: gọi lại API refund cho booking đã hoàn thành công sẽ không
    hoàn tiền 2 lần.
- [x] Lịch sử giao dịch thanh toán cho người dùng
  - → không cần entity mới, `PaymentTransaction.userId` đã đủ dữ liệu để query.
    API tại `GET /api/v1/payment/momo/my-transactions`, sắp xếp mới nhất trước.

### 1.5 Thông báo
- [x] Gửi email xác nhận vé sau khi thanh toán thành công
- [x] Thông báo trong ứng dụng (in-app notification)
  - → notification-service đã có DB: thêm `spring-boot-starter-data-jpa` + `mysql-connector-j`
    vào pom.xml, schema `notification_db` mới. Entity `Notification` (userId, title, content,
    type, isRead, createdAt). API cho user: `GET /api/v1/notifications/my-notifications`,
    `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/{id}/read` (qua
    gateway, route mới `/api/v1/notifications/**`). API nội bộ `POST /internal/create`
    (X-Internal-Secret) để service khác tạo thông báo. Đã nối vào sự kiện thanh toán thành
    công có sẵn (`BookingPaidEmailEvent` qua RabbitMQ, thêm field `userId`): khi gửi mail xác
    nhận vé, tạo luôn 1 thông báo in-app tương ứng — lỗi tạo thông báo không làm retry gửi
    lại mail. Bổ sung `GlobalExceptionHandler` còn thiếu ở notification-service.
- [x] Nhắc lịch trước giờ chiếu (email/push)
  - → không dùng `NotificationTemplate` (nội dung sinh động, chưa cần template hóa) — tái dùng
    entity `Notification` sẵn có. booking-service thêm field `Booking.reminderSent` (chỉ gửi
    đúng 1 lần/booking) + scheduler `ShowtimeReminderJob` (`@Scheduled(fixedRate=5 phút)`) quét
    booking PAID chưa nhắc, gom theo `showtimeId` để giảm số lần gọi catalog-service, nếu suất
    chiếu bắt đầu trong vòng 60 phút (`app.reminder.before-minutes`, cấu hình được) thì gọi
    `POST /api/v1/notifications/internal/create` tạo thông báo in-app loại `SHOWTIME_REMINDER`.
- [ ] Push notification (mobile/web push)
  - → dùng chung `Notification` ở trên, thêm field `channel` (EMAIL/PUSH/IN_APP)
- [ ] Email khuyến mãi / marketing
  - → dùng chung `NotificationTemplate`, không cần entity riêng

---

## Giai đoạn 2 — Kinh doanh & tăng trưởng

- [ ] Mã khuyến mãi / voucher / giảm giá
  - → Entity cần thêm: `Voucher` (code, discountType, value, expiryDate, usageLimit), `VoucherUsage` (voucherId, userId, bookingId)
- [ ] Chương trình tích điểm thành viên (loyalty points)
  - → Entity cần thêm: `PointTransaction` (userId, points, type, relatedBookingId)
- [ ] Hạng thành viên (membership tier)
  - → Entity cần thêm: `MembershipTier` (tier, minPoints, benefits) + field `tierId` trên `User`
- [ ] Giới thiệu bạn bè (referral)
  - → Entity cần thêm: `Referral` (referrerId, refereeId, rewardStatus)
- [ ] Đặt vé nhóm / doanh nghiệp
  - → Entity cần thêm: `Organization` (businessAccount) + field `orgId` trên `Booking`

---

## Giai đoạn 3 — Quản trị & vận hành rạp

- [ ] Dashboard thống kê doanh thu, số vé bán, tỷ lệ lấp đầy phòng
  - → không bắt buộc entity mới, query tổng hợp từ `Booking`/`PaymentTransaction`; có thể thêm `RevenueSnapshot` nếu muốn cache
- [ ] Quản lý tài khoản người dùng (khóa/mở khóa, đổi vai trò)
  - → bổ sung field `status`/`isLocked` vào `User` — auth_db
- [ ] Phân quyền nhân viên rạp theo vai trò (staff, manager, admin)
  - → Entity cần thêm: `StaffAssignment` (userId, cinemaId, roleInCinema) — auth_db hoặc catalog_db
- [ ] Xuất báo cáo (Excel/PDF)
  - → không cần entity mới
- [ ] Cấu hình giá vé linh hoạt theo khung giờ / ngày lễ / hạng ghế
  - → Entity cần thêm: `PricingRule` (dayType, timeRange, seatType, multiplier) — catalog_db

---

## Giai đoạn 4 — Nền tảng kỹ thuật (production-readiness)

- [x] API Gateway định tuyến + xác thực JWT tập trung
- [x] Phân quyền admin tại gateway cho route ghi dữ liệu
- [x] Rate limiting cơ bản tại gateway
- [x] `.gitignore` bảo vệ file secret (`.env`, `.idea`...)
- [ ] Quản lý secret tập trung khi deploy (Vault / AWS Secrets Manager...)
- [ ] Service discovery (Eureka/Consul) thay vì URL cứng giữa các service
- [ ] Circuit breaker / retry / timeout chuẩn hóa (Resilience4j) cho gọi REST nội bộ
- [x] Unit test cho logic nghiệp vụ (đặc biệt `BookingServiceImpl`, `MomoServiceImpl`)
  - → `BookingServiceImplTest` (21 test, Mockito): `confirmPayment` (not found / sai chủ / idempotent khi đã PAID /
    booking CANCELLED / hết hạn giữ chỗ / happy path sinh vé QR), `cancelBooking` (PENDING không hoàn tiền /
    PAID + suất chiếu đã bắt đầu / auto-refund SUCCESS→COMPLETED / bị từ chối→FAILED / mất kết nối→giữ PENDING),
    `getMyBookings` (tính `expiresInSeconds`), `getTickets` + `checkInTicket` (các nhánh bảo mật/trạng thái).
  - → `MomoServiceImplTest` (14 test): `refundPayment` (not found / chưa SUCCESS / idempotent / transId test không
    hợp lệ→FAILED / MoMo chấp nhận→SUCCESS + lưu momoRefundTransId / MoMo từ chối→FAILED / lỗi mạng→FAILED),
    `handleBookingConfirmResult` (payment null / đã SUCCESS / confirm fail giữ trạng thái / confirm ok set paidAt),
    `confirmBookingAndMarkSuccess` (dưới giới hạn retry→publish / chạm giới hạn→PAYMENT_REVIEW / publish lỗi→lastError).
  - → 2 test `@SpringBootTest contextLoads` mặc định (booking + payment) đánh `@Disabled` vì cần MySQL/Redis/RabbitMQ thật.
- [ ] Integration test luồng đặt vé end-to-end
- [ ] CI/CD pipeline tự động (build, test, deploy)
- [ ] Container hóa đầy đủ 6 service trong `docker-compose.yml` (hiện chỉ có api-gateway)
- [ ] Health check endpoint (Spring Actuator)
- [ ] Logging tập trung (ELK/Loki)
- [ ] Metrics & alerting (Prometheus/Grafana)
- [ ] Tài liệu API tự động (Swagger/OpenAPI)
- [ ] HTTPS/SSL cho môi trường production
- [x] Dọn dead code (`BookingProcessService`, `TicketEmailMessage`, `EmailListenerService`,
  `RouteValidator`, Feign `CatalogClient` không dùng — đã xóa; gộp 2 route catalog trùng nhau
  trong `api-gateway/application.yaml`)

---

## Giai đoạn 5 — Mở rộng nâng cao

- [ ] Ứng dụng mobile (iOS/Android)
  - → không cần entity mới (dùng chung API/DB hiện có)
- [ ] Đa ngôn ngữ (i18n)
  - → Entity cần thêm: `MovieTranslation` (movieId, locale, title, description) — catalog_db
- [ ] Gợi ý cá nhân hóa (AI/recommendation engine)
  - → Entity cần thêm: `ViewHistory`/`SearchHistory` (userId, movieId, timestamp)
- [ ] Chatbot hỗ trợ khách hàng
  - → Entity cần thêm: `ChatSession`, `ChatMessage` (nếu cần lưu lịch sử)
- [ ] Tích hợp bán vé qua đối tác/affiliate
  - → không cần entity mới ở giai đoạn đầu (qua API layer)

---

## Tổng kết nhanh

| Giai đoạn | Đã hoàn thành | Tổng số mục |
|---|---|---|
| 1 — MVP lõi | 39 | 40 |
| 2 — Kinh doanh & tăng trưởng | 0 | 5 |
| 3 — Quản trị & vận hành | 0 | 5 |
| 4 — Nền tảng kỹ thuật | 6 | 17 |
| 5 — Mở rộng nâng cao | 0 | 5 |

**Nhận xét:** Luồng lõi kỹ thuật khó nhất (giữ ghế, đồng thời, thanh toán MoMo thật) đã hoàn thiện tốt. Khoảng trống lớn nhất hiện nay là **trải nghiệm sau khi đặt vé** (lịch sử vé, hủy/hoàn tiền, QR check-in) và **nền tảng production-readiness** (test, CI/CD, observability, bảo mật secret).

---

## Entity DB ưu tiên cần thêm (Giai đoạn 1)

| Entity | Service / DB | Field chính | Phục vụ tính năng |
|---|---|---|---|
| `RefreshToken` ✅ đã tạo | auth_db | userId, token, revoked, expiresAt | đăng xuất thật |
| `PasswordResetToken` | auth_db | userId, token, expiresAt | quên mật khẩu |
| `EmailVerificationToken` | auth_db | userId, token, expiresAt | xác minh email |
| `Review` ✅ đã tạo | catalog_db | movieId, userId, rating, comment | đánh giá phim |
| `Favorite` ✅ đã tạo | catalog_db | userId, movieId | watchlist |
| `Ticket` ✅ đã tạo | booking_db | bookingSeatId, qrCode, checkedInAt, checkedInBy | vé QR + check-in |
| `RefundTransaction` ✅ đã tạo | payment_db | paymentTransactionId, amount, reason, status | hoàn tiền |
| `Notification` ✅ đã tạo | notification_db (**mới**) | userId, title, content, type, isRead, createdAt | thông báo trong app |

Ghi chú: `notification-service` đã có schema `notification_db` (entity `Notification` ✅) — vẫn cần thêm `NotificationTemplate` riêng khi làm "nhắc lịch trước giờ chiếu" / "email khuyến mãi".
