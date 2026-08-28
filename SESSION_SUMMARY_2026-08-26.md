# Tóm tắt công việc — 2026-08-26

> Ghi lại nội dung đã trao đổi/thực hiện trong phiên làm việc này với Claude Code cho dự án `movie_ticket_microservice`. Tiếp nối `SESSION_SUMMARY_2026-08-24.md` (phiên 2).

## 1. Vá bảo mật (bước 0 theo roadmap)
- Tạo `.gitignore` ở root repo (trước đó chỉ có ở từng service, root hoàn toàn chưa được bảo vệ) — chặn `.env`, `.idea/`, log, key file. Xác nhận `.env` chưa từng bị commit trong lịch sử trước đó.
- Phát hiện `.env.example` (tạo ở phiên trước) chứa **secret thật** y hệt `.env` (JWT_SECRET, MOMO keys, Cloudinary keys, MAIL_PASSWORD cũ...) thay vì placeholder — đã thay toàn bộ bằng `<your_xxx>` trước khi commit.

## 2. Dọn việc dở dang của phiên 2 (2026-08-24)
- Bật lại Docker (mysql, redis, rabbitmq) + cả 5 service backend đã tắt từ phiên trước.
- Sửa 2 bug pre-existing đã phát hiện ở phiên 2, verify bằng test thật (curl + publish message trực tiếp vào RabbitMQ):
  1. `payment-service`: `app.auth-service-url` mặc định thiếu path `/api/v1/auth` — sửa cả `application.yaml` và `.env`.
  2. `.env`: `MAIL_PASSWORD` bị Gmail từ chối — user tự tạo App Password mới, verify gửi mail thật thành công.
- Commit code RabbitMQ request/reply (payment↔booking confirm) + timeout RestTemplate/Feign đã làm ở phiên 2 nhưng chưa commit (`750e05f`).

## 3. Tính năng mới hoàn thành (Giai đoạn 1 — MVP lõi, checklist tăng từ 24/40 → 30/40)

### 3.1 auth-service — hoàn thiện nhóm "Xác thực & tài khoản"
- **Đăng xuất** (`5c98501`): entity `RefreshToken` (userId, token, revoked, expiresAt). Login lưu token vào DB, `/refresh` kiểm tra revoke trong DB, `/logout` mới đánh dấu revoked. Kèm sửa bug pre-existing: refresh JWT không mang claim `role` → mất quyền admin sau mỗi lần refresh.
- **Quên mật khẩu / đặt lại mật khẩu** (`4c8f174`): entity `PasswordResetToken`. `/forgot-password` không tiết lộ email có tồn tại hay không, `/reset-password` thu hồi toàn bộ refresh token khi đổi thành công. notification-service thêm endpoint gửi mail riêng.
- **Đổi mật khẩu** (`6d0af77`): `/change-password` (cần access token), không cần entity mới. Phát hiện + sửa **bug nghiêm trọng** lúc test: JWT ký deterministic khiến 2 lần login cùng giây sinh token trùng hệt, va unique constraint mới thêm → login lỗi 500. Sửa bằng cách thêm claim `jti` ngẫu nhiên.
- **Xác minh email khi đăng ký** (`8023af0`): entity `EmailVerificationToken`. Đăng ký tự gửi mail xác minh; **quyết định có chủ đích: không chặn login** khi chưa xác minh (chỉ theo dõi qua `User.emailVerified`) để không phá vỡ user cũ đã có trong DB.

### 3.2 catalog-service — mở rộng nhóm "Danh mục phim & rạp"
- **Đánh giá & bình luận phim** (`b96f41b`): entity `Review` (unique theo movieId+userId). API đặt tại `/api/v1/catalog/reviews` (top-level, không lồng dưới `/movies/**`) để tránh bị gateway coi là API quản trị cần quyền ADMIN. Nhân tiện wire lại `DuplicateResourceException` (tồn tại sẵn trong code nhưng chưa từng được dùng/xử lý).
- **Danh sách yêu thích / watchlist** (`058afee`): entity `Favorite`. API tại `/api/v1/catalog/favorites` — khác review ở chỗ `GET` cũng cần đăng nhập (danh sách riêng tư), phải thêm route xác thực tương ứng ở gateway.
- Cả 2 tính năng đều phát hiện gateway **thiếu route** cho path mới (sẽ 404 nếu không thêm) — đã sửa `api-gateway/application.yaml`.

### 3.3 Bỏ qua có chủ đích
- **Đăng nhập qua Google/Facebook**: cần OAuth Client ID/Secret thật từ Google Cloud Console / Facebook Developer mà chỉ user mới tạo được — đã hỏi và được đồng ý bỏ qua, chuyển sang mục kế tiếp trong checklist.

## 4. Quy trình làm việc trong phiên
- Toàn bộ tính năng đều: code → compile kiểm tra → restart service local → test end-to-end thật bằng curl (qua gateway thật khi liên quan đến quyền/route) → cập nhật `FEATURE_CHECKLIST.md` → hỏi xác nhận → commit riêng từng tính năng → push.
- Phát hiện phụ (môi trường, không phải bug code): gọi REST qua `localhost` từ auth-service bị JDK HttpClient trên Windows race IPv6/IPv4 gây lỗi ngẫu nhiên "Request cancelled" — dùng `127.0.0.1` thì ổn định. Chỉ ảnh hưởng khi chạy service local ngoài Docker.

## File đã tạo/cập nhật trong phiên này
- Root: `.gitignore` (mới), `.env` (sửa AUTH_SERVICE_URL + MAIL_PASSWORD), `.env.example` (dọn secret thật), `FEATURE_CHECKLIST.md`, `SESSION_SUMMARY_2026-08-26.md` (file này).
- `api-gateway`: `application.yaml` (thêm route `/reviews/**` public-GET, `/favorites/**` protected-GET).
- `auth-service`: entity `RefreshToken`, `PasswordResetToken`, `EmailVerificationToken`; repository tương ứng; `RestTemplateConfig`; DTO đăng xuất/quên-đổi mật khẩu/xác minh email; `JwtUtils` (thêm `jti`, `getExpirationDateFromJwt`); `UserService`/`UserServiceImpl`/`AuthController`/`SecurityConfig` (mở rộng nhiều endpoint mới); `User` entity (thêm `emailVerified`); `UserResponseDTO` (thêm field).
- `catalog-service`: entity `Review`, `Favorite`; repository/service/controller/DTO tương ứng; `GlobalExceptionHandler` (thêm handler `DuplicateResourceException`).
- `notification-service`: `EmailService`/`EmailServiceImpl`/`NotificationController` (thêm gửi mail đặt lại mật khẩu + xác minh email); DTO tương ứng.

## Commit đã push lên `origin/main` (thứ tự thời gian)
1. `748139b` chore: thêm .gitignore cấp root
2. `0e4364c` docs: quy tắc làm việc, checklist, tóm tắt phiên, kịch bản test
3. `750e05f` feat: RabbitMQ request/reply cho payment↔booking + timeout REST client (việc dở dang phiên 2)
4. `5c98501` feat: đăng xuất + sửa bug role null
5. `4c8f174` feat: quên mật khẩu / đặt lại mật khẩu
6. `6d0af77` feat: đổi mật khẩu + sửa bug refresh token trùng khi login cùng giây
7. `8023af0` feat: xác minh email khi đăng ký
8. `b96f41b` feat: đánh giá & bình luận phim
9. `058afee` feat: danh sách yêu thích / watchlist

## Trạng thái môi trường cuối phiên (đang chạy trên máy dev)
- Docker: `mysql-db`, `redis-cache`, `rabbitmq-broker` đang chạy.
- Cả 6 service backend + `api-gateway` đang chạy local: auth-service (8083), catalog-service (8081), booking-service (8082), payment-service (8084), notification-service (8085), api-gateway (8080).
- Dữ liệu test còn trong DB: user `qa-test-1787566636@example.com`, `logouttest1`, `verifytest1`, `reviewtest2`; vài review/favorite test cho movie `QA Test Movie`.

## Việc tiếp theo (chưa thực hiện)
- Giai đoạn 1 còn thiếu: đăng nhập Google/Facebook (chờ OAuth credential từ user), vé QR + check-in, hủy vé + hoàn tiền, thêm phương thức thanh toán khác, thông báo in-app, gợi ý combo bắp nước.
- Giai đoạn 4 (nền tảng kỹ thuật): circuit breaker/Resilience4j vẫn là bước tiếp theo đã đề cập nhiều lần nhưng chưa bắt đầu; unit/integration test, CI/CD, container hóa đầy đủ, logging/metrics tập trung, Swagger vẫn chưa làm.
- Cân nhắc dọn dữ liệu test tích lũy qua nhiều phiên trong DB nếu muốn DB sạch trước khi demo.
