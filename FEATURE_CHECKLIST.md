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
- [ ] Xác minh email khi đăng ký
  - → Entity cần thêm: `EmailVerificationToken` (userId, token, expiresAt) — auth_db
- [ ] Đăng nhập qua mạng xã hội (Google/Facebook)
  - → Entity cần thêm: `SocialAccount` (provider, providerUserId, userId) — auth_db

### 1.2 Danh mục phim & rạp
- [x] CRUD phim (thêm/sửa/xóa mềm/xem)
- [x] Tìm kiếm phim theo tên
- [x] Lọc phim theo trạng thái (đang chiếu/sắp chiếu)
- [x] Cập nhật poster, trailer phim
- [x] CRUD rạp chiếu, tìm kiếm theo tên/thành phố
- [x] CRUD phòng chiếu kèm sơ đồ ghế (grid layout)
- [x] Quản lý loại ghế: thường / VIP / đôi (couple) / bảo trì
- [x] CRUD suất chiếu
- [x] Xem lịch chiếu theo rạp + ngày, theo phim + ngày
- [x] CRUD snack (bắp nước), cập nhật ảnh
- [ ] Đánh giá & bình luận phim (review, rating)
  - → Entity cần thêm: `Review` (movieId, userId, rating, comment, createdAt) — catalog_db
- [ ] Danh sách yêu thích / watchlist
  - → Entity cần thêm: `Favorite` (userId, movieId) — catalog_db
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
- [ ] Xem lịch sử đặt vé của tôi (danh sách booking theo user)
  - → không cần entity mới, `Booking.userId` đã đủ dữ liệu để query
- [ ] Hủy vé đã thanh toán + hoàn tiền
  - → bổ sung field `cancellationReason`, `refundStatus` vào `Booking` — booking_db (phần hoàn tiền thực tế xem `RefundTransaction` ở mục Thanh toán)
- [ ] Vé điện tử có mã QR
  - → Entity cần thêm: `Ticket` (bookingSeatId, qrCode, checkedInAt, checkedInBy) — booking_db
- [ ] Check-in tại rạp (quét QR soát vé)
  - → dùng chung entity `Ticket` ở trên
- [ ] Chọn combo bắp nước theo gói ưu đãi
  - → Entity cần thêm: `SnackCombo` (name, items[], price) — catalog_db

### 1.4 Thanh toán
- [x] Thanh toán qua MoMo (tạo giao dịch, lấy QR/link thanh toán)
- [x] Xác thực callback IPN từ MoMo (chống giả mạo chữ ký, chống xử lý lặp)
- [x] API test giả lập thanh toán thành công (môi trường dev)
- [ ] Thêm phương thức thanh toán khác (VNPay, ZaloPay, thẻ ngân hàng)
  - → tổng quát hóa field `provider`/`method` trong `PaymentTransaction` — payment_db
- [ ] Hoàn tiền tự động khi hủy vé
  - → Entity cần thêm: `RefundTransaction` (paymentTransactionId, amount, reason, status, refundedAt) — payment_db
- [ ] Lịch sử giao dịch thanh toán cho người dùng
  - → không cần entity mới, `PaymentTransaction.userId` đã đủ dữ liệu để query

### 1.5 Thông báo
- [x] Gửi email xác nhận vé sau khi thanh toán thành công
- [ ] Thông báo trong ứng dụng (in-app notification)
  - → notification-service hiện **chưa có DB** — cần thêm schema `notification_db` + Entity `Notification` (userId, title, content, type, isRead, createdAt)
- [ ] Nhắc lịch trước giờ chiếu (email/push)
  - → Entity cần thêm: `NotificationTemplate` — notification_db
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
- [ ] Unit test cho logic nghiệp vụ (đặc biệt `BookingServiceImpl`, `MomoServiceImpl`)
- [ ] Integration test luồng đặt vé end-to-end
- [ ] CI/CD pipeline tự động (build, test, deploy)
- [ ] Container hóa đầy đủ 6 service trong `docker-compose.yml` (hiện chỉ có api-gateway)
- [ ] Health check endpoint (Spring Actuator)
- [ ] Logging tập trung (ELK/Loki)
- [ ] Metrics & alerting (Prometheus/Grafana)
- [ ] Tài liệu API tự động (Swagger/OpenAPI)
- [ ] HTTPS/SSL cho môi trường production
- [ ] Dọn dead code (`BookingProcessService` — logic đã bị comment, không còn dùng)

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
| 1 — MVP lõi | 27 | 40 |
| 2 — Kinh doanh & tăng trưởng | 0 | 5 |
| 3 — Quản trị & vận hành | 0 | 5 |
| 4 — Nền tảng kỹ thuật | 4 | 17 |
| 5 — Mở rộng nâng cao | 0 | 5 |

**Nhận xét:** Luồng lõi kỹ thuật khó nhất (giữ ghế, đồng thời, thanh toán MoMo thật) đã hoàn thiện tốt. Khoảng trống lớn nhất hiện nay là **trải nghiệm sau khi đặt vé** (lịch sử vé, hủy/hoàn tiền, QR check-in) và **nền tảng production-readiness** (test, CI/CD, observability, bảo mật secret).

---

## Entity DB ưu tiên cần thêm (Giai đoạn 1)

| Entity | Service / DB | Field chính | Phục vụ tính năng |
|---|---|---|---|
| `RefreshToken` ✅ đã tạo | auth_db | userId, token, revoked, expiresAt | đăng xuất thật |
| `PasswordResetToken` | auth_db | userId, token, expiresAt | quên mật khẩu |
| `EmailVerificationToken` | auth_db | userId, token, expiresAt | xác minh email |
| `Review` | catalog_db | movieId, userId, rating, comment | đánh giá phim |
| `Favorite` | catalog_db | userId, movieId | watchlist |
| `Ticket` | booking_db | bookingSeatId, qrCode, checkedInAt, checkedInBy | vé QR + check-in |
| `RefundTransaction` | payment_db | paymentTransactionId, amount, reason, status | hoàn tiền |
| `Notification` | notification_db (**mới**) | userId, title, content, type, isRead, createdAt | thông báo trong app |

Ghi chú: `notification-service` hiện chưa có database nào — cần khởi tạo schema `notification_db` trước khi thêm entity `Notification`/`NotificationTemplate`.
