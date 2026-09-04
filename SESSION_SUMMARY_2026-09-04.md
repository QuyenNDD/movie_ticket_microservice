# Tóm tắt công việc — 2026-09-04

> Ghi lại nội dung đã trao đổi/thực hiện trong phiên làm việc này với Claude Code cho dự án `movie_ticket_microservice`. Tiếp nối `SESSION_SUMMARY_2026-09-03.md` (phiên 5).

## 0. Định hướng phiên

Đầu phiên đọc `CLAUDE.md` + `DEPLOYMENT_ROADMAP.md` + `SESSION_SUMMARY_2026-09-03.md` + `FEATURE_CHECKLIST.md`. Sau đó user yêu cầu **rà soát lại toàn bộ các "nợ kỹ thuật còn treo"** đã liệt kê ở cuối `SESSION_SUMMARY_2026-09-03.md`, đọc code thật để xác định mục nào còn tồn tại, rồi **sửa dứt điểm các mục còn lại**.

---

## 1. Rà soát nợ kỹ thuật (đọc code + đọc lại toàn bộ file summary)

| Nợ kỹ thuật | Kết luận sau khi đọc code |
|---|---|
| `.env.example` không nhất quán URL giữa gateway và các service | **Đã hết** từ phiên 2026-09-03 (mục 9): gateway đổi sang `*_SERVICE_URI` (base host) tách khỏi `*_SERVICE_URL` (có path), `.env.example` viết lại chỉ còn secret. Đoạn "nợ còn treo" trong file summary phiên trước là viết ở nửa đầu phiên, trước khi nửa sau fix. → Xoá memory `env-example-url-inconsistency.md` (không còn đúng). |
| catalog-service có 2 filter chồng nhau (`ServiceAccessFilter` + `InternalApiFilter`) | **Còn tồn tại** — cả 2 đều `@Component` active, cùng gác đúng 4 endpoint giá nội bộ. `ServiceAccessFilter.isInternalCatalogApi()` bao trùm hoàn toàn `InternalApiFilter` (regex còn chặt hơn). → Xoá `InternalApiFilter`. |
| `CorsConfig` (gateway) hardcode danh sách origin FE | **Còn tồn tại** — hardcode 4 origin `localhost:3000/5173`. → Externalize qua env `CORS_ALLOWED_ORIGINS`. |
| Unit test chưa cover `holdSeats` (validate ghế) + `processIpn` của MoMo | **Còn tồn tại** — không có test nào. → Viết bổ sung. |

Không có file `SESSION_SUMMARY_*` nào trước đây ghi việc sửa 3 nợ sau (chỉ #1 được xử lý gọn trong phiên 2026-09-03 nhưng note chưa cập nhật lại).

---

## 2. Xoá `InternalApiFilter` (catalog-service) — gộp filter chồng nhau

- Xoá `catalog-service/src/main/java/com/movie/catalog_service/config/InternalApiFilter.java`.
- `ServiceAccessFilter` giữ nguyên, xử lý đủ cả 3 lớp: API nội bộ (`X-Internal-Secret`) cho 4 endpoint giá (`/showtimes/*/seats/*/price`, `/snacks/*/price`, `/snack-combos/*/price`, `/rooms/internal/*/seats`), GET public, và API ghi (`X-Gateway-Secret`).
- `mvnw -o compile` catalog-service OK. `mvn test` OK (chỉ 1 test skip).

## 3. Externalize CORS origin (api-gateway)

- `CorsConfig.java`: thay list hardcode bằng `@Value("${app.cors.allowed-origins}")` → `List<String>` (Spring tự split theo dấu phẩy).
- `api-gateway/application.yaml`: thêm
  ```yaml
  app:
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://127.0.0.1:3000,http://localhost:5173,http://127.0.0.1:5173}
  ```
- `docker-compose.yml`: thêm `CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-<4 origin dev>}` vào env của `api-gateway`.
- `.env.example`: thêm mục comment `# CORS_ALLOWED_ORIGINS=https://your-frontend-domain` (khi có domain FE thật thì set).
- `mvnw -o compile` + `mvn test` api-gateway OK.

## 4. Unit test bổ sung cho `holdSeats` + `processIpn`

### 4.1 `BookingServiceImplTest` — nested class `HoldSeats` (11 test mới, tổng 21 → 32)

Mock Mockito thuần (thêm mock `ValueOperations<String,String>` cho `redisTemplate.opsForValue()`). Các nhánh:
- thiếu `showtimeId` / danh sách ghế rỗng → chặn sớm
- ghế bị trùng trong request
- ghế đã PAID trong DB (`checkIfSeatsArePaid`)
- ghế chọn không thuộc phòng chiếu của suất
- ghế đang bị người khác giữ (Redis key tồn tại, `allowSelectedLockedSeats=false`)
- ghế bảo trì
- rule COUPLE: 5 ghế COUPLE = 10 chỗ ngồi > `MAX_SEATS_PER_BOOKING` (8)
- chọn ghế để lại **ghế trống lẻ cô lập** (chọn s1 + s3 kẹp s2 giữa 2 ghế đang chọn)
- mất lock Redis giữa chừng (`setIfAbsent` seat thứ 2 trả false) → ném lỗi + **nhả lock đã giữ** của seat thứ 1 (`verify redisTemplate.delete(k1)`)
- happy path: lưu booking `PENDING`, `totalPrice` = tổng giá ghế, giữ đủ lock

### 4.2 `MomoServiceImplTest` — nested class `ProcessIpn` (11 test mới, tổng 14 → 25)

Helper `signedIpn(...)` tự sinh chữ ký HMAC hợp lệ đúng thuật toán `validateMomoSignature` (dùng `HmacSHA256Util.encode` với secret test). Các nhánh:
- chữ ký sai → ném "Chữ ký IPN không hợp lệ", không đụng gì DB
- không tìm thấy payment transaction theo orderId
- payment đã `SUCCESS` → idempotent no-op (IPN lặp)
- `transId` đã xử lý thành công trước đó (`existsByTransIdAndStatus`) → no-op
- `resultCode` != 0 (vd 1006) → payment `FAILED` + `lastError`, `nextRetryAt` null
- `resultCode` 1005 → payment `EXPIRED`
- `orderId` suy ra bookingId lệch với `payment.bookingId` → `PAYMENT_REVIEW`
- số tiền MoMo lệch `payment.amount` → `PAYMENT_REVIEW`
- booking đã `PAID` sẵn → payment `SUCCESS` + set `transId`
- booking `CANCELLED` sau khi MoMo báo thành công → `REFUND_REQUIRED`
- booking còn `PENDING` hợp lệ → `CONFIRM_PENDING` + `bookingConfirmRequestPublisher.publish(...)`

### 4.3 Kết quả

- `booking-service`: `mvn test` → **32 pass** (`BookingServiceImplTest`), BUILD SUCCESS.
- `payment-service`: `mvn test` → **25 pass** (`MomoServiceImplTest`), BUILD SUCCESS.
- `catalog-service`, `api-gateway`: `mvn test` BUILD SUCCESS (chỉ contextLoads skip).

---

## 5. File đã tạo/sửa trong phiên

- **Root**:
  - `FEATURE_CHECKLIST.md` — cập nhật ghi chú mục "Unit test cho logic nghiệp vụ" (holdSeats + processIpn đã cover, số test 21→32 / 14→25).
  - `.env.example` — thêm mục `CORS_ALLOWED_ORIGINS` (comment).
  - `docker-compose.yml` — thêm `CORS_ALLOWED_ORIGINS` vào env `api-gateway`.
  - `DEPLOYMENT_ROADMAP.md` — cập nhật nhật ký phiên + trạng thái.
  - `SESSION_SUMMARY_2026-09-04.md` (file này).
- **api-gateway**:
  - `src/main/java/com/movie/api_gateway/config/CorsConfig.java` — externalize origin qua `@Value`.
  - `src/main/resources/application.yaml` — thêm `app.cors.allowed-origins`.
- **catalog-service**:
  - **Xoá** `src/main/java/com/movie/catalog_service/config/InternalApiFilter.java`.
- **booking-service**:
  - `src/test/java/com/movie/booking_service/service/BookingServiceImplTest.java` — thêm nested class `HoldSeats` (11 test) + mock `ValueOperations`.
- **payment-service**:
  - `src/test/java/com/movie/payment_service/service/MomoServiceImplTest.java` — thêm nested class `ProcessIpn` (11 test).
- **Memory (Claude)**:
  - Xoá `env-example-url-inconsistency.md` (nợ đã được sửa ở phiên trước) + gỡ khỏi `MEMORY.md`; gỡ link chết trong `run-services-locally.md`.

## 6. Trạng thái nợ kỹ thuật sau phiên

Tất cả các nợ kỹ thuật "còn treo" liệt kê ở cuối `SESSION_SUMMARY_2026-09-03.md` **đã xử lý xong**:
- ✅ `.env.example` URL inconsistency (thực ra đã fix ở phiên trước)
- ✅ catalog-service 2 filter chồng nhau → gộp còn 1
- ✅ `CorsConfig` hardcode origin → externalize
- ✅ Unit test `holdSeats` + `processIpn`
- (`CorsConfig` externalize đồng thời đóng luôn ý "nên externalize danh sách origin FE" ghi ở phiên trước)

## 7. Việc TIẾP THEO (chưa thực hiện — theo `DEPLOYMENT_ROADMAP.md` Giai đoạn A còn lại)

1. **CI — GitHub Actions**: workflow build + `mvn test` cả 6 service khi push/PR; badge README.
2. **Postman collection**: full luồng đặt vé, export `.json` vào repo.
3. **Dọn secret default hardcode** trong `application.yaml`/`.yml` (`JWT_SECRET`/`GATEWAY_SECRET`/`INTERNAL_SECRET` đang để giá trị hex thật làm default).
4. Sang **Giai đoạn B**: chốt host (Oracle Cloud cần thẻ / PC + Cloudflare Tunnel) rồi deploy.

Nợ kỹ thuật còn lại (mức thấp, không chặn deploy):
- Integration test luồng đặt vé end-to-end (Testcontainers) — Giai đoạn 4 checklist.
- Chuẩn hoá `.env.example` khớp hoàn toàn biến compose (Giai đoạn C).

---

# Bổ sung — phiên 2 cùng ngày (2026-09-04): CI GitHub Actions

## 8. `.github/workflows/ci.yml` — build + test cả 6 service

- Trigger: push / PR vào `main`. `permissions: contents: read`, `concurrency` hủy run cũ khi push liên tiếp.
- **Matrix 6 service** (`fail-fast: false`): mỗi job `actions/checkout@v4` → `actions/setup-java@v4` (temurin 21, `cache: maven`) → `./mvnw -B -ntp -e verify` trong thư mục service → upload `target/surefire-reports/` làm artifact (`if: always()`).
- Chọn **JDK 21** (pom target `java.version=17` nhưng test đã verify xanh trên 21 ở local, Spring Boot 3.5.14 chạy tốt cả 2).
- Dùng `verify` (không chỉ `test`) để CI cũng chạy `package` + `spring-boot:repackage` — xác nhận jar build được (thứ Docker sẽ build).

## 9. Đặt bit thực thi cho `mvnw`

6 file `*/mvnw` trước ở mode `100644` (không executable) → `git update-index --chmod=+x` thành `100755` để runner Linux gọi `./mvnw` trực tiếp (không cần `chmod` trong workflow).

## 10. Badge + verify

- Thêm badge `[![CI](...actions/workflows/ci.yml/badge.svg)]` vào `README.md` (thay comment placeholder cũ).
- Local: `./mvnw verify` notification-service OK (test + repackage jar).
- **Push lên `main` → run CI đầu tiên: SUCCESS, 6/6 job xanh** (run `33832839853`).

## 11. File tạo/sửa phiên 2

- **Mới**: `.github/workflows/ci.yml`.
- **Sửa**: `README.md` (badge), 6 × `*/mvnw` (chmod +x), `DEPLOYMENT_ROADMAP.md` (tick "CI — GitHub Actions" Giai đoạn A + nhật ký), `FEATURE_CHECKLIST.md` (ghi chú "CI đã có / CD chưa" dưới mục CI/CD pipeline), `SESSION_SUMMARY_2026-09-04.md` (file này).

---

# Bổ sung — phiên 3 cùng ngày (2026-09-04): Postman collection

## 13. `postman/` — collection luồng đặt vé đầu-cuối

- **`movie-ticket.postman_collection.json`** (schema v2.1.0), **`movie-ticket-local.postman_environment.json`**, **`postman/README.md`**.
- 5 folder chạy tuần tự qua `api-gateway:8080`, test script tự lưu + chuyền biến (`adminToken`/`userToken`/`showtimeId`/`bookingId`/`seatId1,2`/`qrCode`):
  - `0 · Admin — seed data`: register admin → (bước thủ công: `UPDATE users SET role='ADMIN'`) → login admin → tạo cinema / room (5×8, hàng D=VIP, E=COUPLE) / movie / showtime (ngày mai 19:00).
  - `1 · Auth (user)`: register → login → `/me`.
  - `2 · Browse catalog`: list movies → showtimes theo `movieId`+date → seat map (test script tự nhặt 2 ghế `AVAILABLE` non-COUPLE → `seatId1/2`).
  - `3 · Hold seats & pay`: hold → booking detail → **Simulate payment success (dev)** (`/payment/momo/test/{id}/success`) hoặc **Create real MoMo QR** → **Poll booking until PAID** (tự lặp ≤20 lần, chờ pipeline RabbitMQ) → get tickets (QR).
  - `4 · Post-booking`: my-bookings, my-transactions, check-in (ADMIN), cancel + auto refund.
- Collection-level pre-request sinh `runId` ngẫu nhiên (tên đăng ký không trùng khi chạy lại) + tính `showDate`/`showtimeStart` = ngày mai. Auth mặc định `Bearer {{userToken}}`, override no-auth / `{{adminToken}}` theo request.

## 14. Verify bằng Newman trên stack thật

- `docker compose up -d` (9/9 healthy, image build sẵn từ phiên 2026-09-03).
- 2 bug trong collection phát hiện khi verify → sửa:
  - `Create movie`: `releaseDate` phải ≥ hôm nay (rule `isBefore(now)` chặn, message "Release day is not before now" bị ngược nghĩa — nợ cũ) → dùng `{{showDate}}`; `status` phải là `ACTIVE`.
  - `Create room`: `seatType` hợp lệ là `NORMAL`/`VIP`/`COUPLE`/`MAINTENANCE`/`EMPTY` — đã dùng nhầm `SINGLE`.
- Sau khi sửa: `npx newman run ... --env-var runId=990013 --delay-request 500` → **22 request, 31/31 assertion PASS**. Bằng chứng: booking `40a4...` chuyển `PAID` qua RabbitMQ, sinh 2 vé QR, check-in set `checkedInAt`, cancel → `CANCELLED` + `refundStatus=FAILED` (đúng: transId `TEST_...` không refund MoMo thật được).
- Sau verify: `docker compose down`.

## 15. File tạo/sửa phiên 3

- **Mới**: `postman/movie-ticket.postman_collection.json`, `postman/movie-ticket-local.postman_environment.json`, `postman/README.md`.
- **Sửa**: `DEPLOYMENT_ROADMAP.md` (tick "Postman collection" + trạng thái), `SESSION_SUMMARY_2026-09-04.md` (file này). (`FEATURE_CHECKLIST.md` không có mục riêng cho Postman — roadmap Giai đoạn A đã theo dõi.)

## 16. Việc TIẾP THEO

1. ~~CI GitHub Actions~~ ✅
2. ~~Postman collection~~ ✅
3. ~~Dọn secret default hardcode~~ ✅ (phiên 4 bên dưới)
4. Sang **Giai đoạn B**: chốt host (Oracle Cloud / PC + Cloudflare Tunnel) + deploy.

---

# Bổ sung — phiên 4 cùng ngày (2026-09-04): dọn secret default hardcode

## 17. Bỏ giá trị mặc định của secret trong yaml + `@Value`

Các file `application.yaml`/`.yml` đã commit từng chứa **giá trị hex/base64 thật** làm default cho secret dùng chung — bất kỳ ai đọc git đều thấy. Xử lý theo hướng **fail-fast**: bỏ default hẳn, thiếu env → app fail khi khởi động (giống DB creds vốn đã không có default).

- **`JWT_SECRET`**: `api-gateway/application.yaml` `${JWT_SECRET:VmVy...}` → `${JWT_SECRET}`. (auth-service đã không có default từ trước.)
- **`GATEWAY_SECRET`**: bỏ default `a3f8c9e1...` ở **5 file** — api-gateway, booking, payment, notification, catalog.
- **`INTERNAL_SECRET`**: bỏ default `8b7c6d5e...` ở **4 file** — booking, payment, notification, catalog; auth-service đổi `${INTERNAL_SECRET:dev-internal-secret}` → `${INTERNAL_SECRET}` (trước đây auth dùng default khác các service khác → mismatch tiềm ẩn khi chạy không env, giờ hết).
- **2 chỗ `@Value` trong auth-service**: `UserServiceImpl` + `AuthController` `@Value("${app.internal-secret:dev-internal-secret}")` → `@Value("${app.internal-secret}")`.
- `.env.example`: ghi rõ 3 biến này **BẮT BUỘC** (không default), JWT_SECRET ≥ 32 byte, GATEWAY/INTERNAL phải giống nhau ở mọi service.

Grep xác nhận: không còn chuỗi `a3f8c9e1` / `8b7c6d5e` / `VmVyeVNlY3JldEtle` / `dev-internal-secret` nào trong repo.

## 18. Kiểm chứng

- `mvn test` auth-service (có sửa Java) → BUILD SUCCESS.
- 6 test `@SpringBootTest contextLoads` đều `@Disabled` → CI (`./mvnw verify`) không khởi động context → bỏ default không làm CI đỏ.
- `docker compose up -d --build` (rebuild toàn bộ image) → **9/9 container healthy** (các service lấy secret từ `.env` qua compose — fail-fast đúng: có env thì boot bình thường).
- `npx newman run` collection Postman trên stack mới, `runId=990022` → **22 request, 31/31 assertion PASS** (booking PAID, 2 vé QR, check-in, cancel+refund FAILED đúng như test transId).
- Sau verify: `docker compose down`.

## 19. File sửa phiên 4

- `api-gateway/src/main/resources/application.yaml`
- `auth-service/src/main/resources/application.yaml` + `service/UserServiceImpl.java` + `controller/AuthController.java`
- `booking-service/src/main/resources/application.yml`
- `catalog-service/src/main/resources/application.yaml`
- `notification-service/src/main/resources/application.yaml`
- `payment-service/src/main/resources/application.yaml`
- `.env.example` (ghi chú 3 biến bắt buộc), `DEPLOYMENT_ROADMAP.md`, `SESSION_SUMMARY_2026-09-04.md`.

## 20. Trạng thái: Giai đoạn A XONG

Toàn bộ checklist Giai đoạn A (roadmap) đã tick. Việc TIẾP THEO: **Giai đoạn B — Deploy** — cần user chốt host (Oracle Cloud Always Free ARM cần thẻ xác minh, hoặc PC + Cloudflare Tunnel).

---

## 21. Tất cả commit đã push lên `origin/main` trong phiên (thứ tự thời gian)

| Commit | Nội dung |
|---|---|
| `f093b0e` | refactor: gộp filter nội bộ catalog-service, xóa `InternalApiFilter` |
| `85dd6d6` | refactor: externalize danh sách CORS origin ở api-gateway |
| `5c89e56` | test: unit test cho `holdSeats` (booking) và `processIpn` (MoMo) — 32 + 25 test |
| `94df960` | docs: tổng kết phần 1 (dọn nợ kỹ thuật) |
| `bd4bed2` | ci: GitHub Actions build + test cả 6 service (matrix, `./mvnw verify` JDK 21) + chmod +x 6 `mvnw` + badge |
| `77d002a` | docs: tick CI (run đầu xanh 6/6) |
| `faa624b` | ci: `paths-ignore: '**.md'` — commit markdown-only không chạy CI |
| `b7d0e14` | feat: Postman collection luồng đặt vé đầu-cuối (`postman/`, verify Newman 31/31) |
| `20a90b5` | docs: tick Postman collection |
| `2669deb` | chore: bỏ default hardcode của secret trong yaml (fail-fast) — verify compose 9/9 + Newman 31/31 |
| `3f54132` | docs: tick "dọn secret default" + Giai đoạn A xong |

(Ngoài ra 2 commit từ phiên 2026-09-03 chưa push — `d1e0dcf` DEPLOYMENT_ROADMAP — được đẩy kèm ở lần push đầu.)

## 22. Bàn Giai đoạn B — chưa chốt

Trao đổi cuối phiên về host:
- Thẻ dùng cho Oracle chỉ để **xác minh danh tính** (giữ tạm ~25k rồi hoàn) — **không gỡ được thẻ** sau đăng ký (Oracle yêu cầu luôn có 1 payment method), nhưng **không cần gỡ**: chống phí = **không bấm "Upgrade to Pay As You Go"** + chỉ tạo tài nguyên trong danh sách Always Free (VM.Standard.A1.Flex ≤ 4 OCPU/24GB, ≤ 200GB block volume, không tạo Load Balancer trả phí).
- 2 lựa chọn còn để ngỏ: **Oracle Cloud ARM** (link 24/7, rủi ro phí ~0 nếu không upgrade) vs **PC + Cloudflare Tunnel** (rủi ro 0 tuyệt đối, chỉ online khi PC bật).
- **User chưa chốt** — đầu phiên sau quyết định rồi bắt đầu Giai đoạn B.

## 23. Trạng thái môi trường cuối phiên

- Docker: đã `docker compose down` (containers + network xóa; volume `mysql-data` giữ lại — có chứa dữ liệu test từ các lần verify Newman: user `pmt_admin_*`, `pmt_user_*`, cinema/room/movie/showtime demo. Vô hại; xóa bằng `docker compose down -v` nếu muốn sạch).
- Không còn service Java nào chạy local.
- Toàn bộ image `movie-ticket-*` đã build (rebuild ở phiên 4) — `docker compose up -d` (không `--build`) sẽ nhanh.
- `git status` sạch, `main` đồng bộ `origin/main`, CI xanh.
