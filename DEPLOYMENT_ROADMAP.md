# Lộ trình deploy — Movie Ticket Microservices

> **File này là kim chỉ nam xuyên suốt nhiều phiên.** Đầu mỗi phiên đọc file này để biết:
> đang làm tới đâu, việc tiếp theo là gì, và hướng đi đã chốt.
> Việc nào xong thì tick `[x]`, chưa xong để trống `[ ]`.

---

## 1. Mục tiêu

Dự án này là **portfolio để đi xin việc**, cần **triển khai trên môi trường production hoặc gần giống production** — tức có một **link công khai chạy thật** để nhà tuyển dụng click vào, kèm câu chuyện DevOps (container hóa, CI/CD, reverse proxy, HTTPS).

**Khoảng cách tới mục tiêu KHÔNG phải là thêm tính năng.** Giai đoạn 1 (MVP lõi) gần xong (39/40), phần khó nhất (giữ ghế đồng thời, thanh toán MoMo thật) đã hoàn thiện. Khoảng cách là **deployment + DevOps + trình bày**. Phần lớn repo portfolio backend không bao giờ được deploy — một link sống + `docker compose up` chạy được + CI xanh + README có sơ đồ kiến trúc là thứ tạo khác biệt.

---

## 2. Phương pháp đã chọn

### 2.1 Host (chưa chốt cuối — 2 lựa chọn, đều $0)

- [ ] **Oracle Cloud — Always Free ARM (Ampere A1)** — *ưu tiên 1*
  - 4 core ARM + 24GB RAM + 200GB disk, **miễn phí vĩnh viễn** (không phải trial).
  - Cần thẻ tín dụng/ghi nợ để **xác minh danh tính** (không bị trừ tiền nếu chỉ dùng Always Free).
  - ARM → build image **ngay trên instance** (24GB RAM, `docker compose build` thoải mái), không cần cross-compile.
  - Rủi ro: capacity A1 đôi khi hết ở region phổ biến (chọn region vắng / retry); Oracle có thể thu hồi instance idle lâu (cron nhẹ giữ CPU > 0).
- [ ] **PC cá nhân + Cloudflare Tunnel** — *dự phòng nếu không có thẻ*
  - `docker compose up` trên máy + `cloudflared tunnel` → link `https://...` công khai, $0, HTTPS sẵn, không mở port router.
  - Nhược: chỉ online khi PC bật (bật trước cuộc phỏng vấn, hoặc để chạy 24/7).

### 2.2 Giảm tải (tùy chọn — nếu host yếu, đều free tier thật)

| Thành phần | Dịch vụ free | Ghi chú |
|---|---|---|
| Redis | Upstash | 256MB, 10K lệnh/ngày |
| RabbitMQ | CloudAMQP "Little Lemur" | 1M msg/tháng |
| MySQL | TiDB Cloud Serverless | 5GB, tương thích MySQL |

### 2.3 Reverse proxy + HTTPS

- **Caddy** (khuyến nghị — tự xin/gia hạn Let's Encrypt, cấu hình 3 dòng) hoặc **Nginx + certbot**.
- Chỉ expose `api-gateway` (cổng 8080) ra ngoài qua reverse proxy. RabbitMQ UI (15672) chỉ mở tạm để demo.

### 2.4 KHÔNG làm (đã cân nhắc và loại)

- **Kubernetes** — overkill cho portfolio, cluster managed tốn $70+/tháng. Nếu sau này muốn khoe k8s → cài `k3s` single-node trên chính VPS (giai đoạn sau).
- **Frontend** — dùng **Swagger + Postman collection + README walkthrough** làm mặt demo (quyết định 2026-09-03).
- Các tính năng Giai đoạn 2/3 làm ồ ạt — chỉ làm 1–2 mục *khác biệt* SAU KHI deploy (xem Giai đoạn D).

---

## 3. Lộ trình (tick khi xong)

### Giai đoạn A — Chuẩn bị (làm local) — *gần xong*

- [x] Dọn nợ kỹ thuật (dead code, constant-time secret, externalize URL, SLF4J)
- [x] Unit test logic nghiệp vụ rủi ro cao — `BookingServiceImpl` (21 test), `MomoServiceImpl` (14 test)
- [x] Swagger / OpenAPI mỗi service backend
- [x] Actuator health check + liveness/readiness probes (cả 6 service)
- [x] Resilience4j — circuit breaker + retry + timeout cho REST nội bộ, map lỗi downstream → HTTP 503
- [x] `mvn test` cả 6 service BUILD SUCCESS (`@Disabled` các test `contextLoads` cần hạ tầng)
- [x] `Dockerfile` multi-stage cho cả 6 service + `.dockerignore`
- [x] `docker-compose.yml` full stack — `docker compose up -d --build` → 9 container healthy, test E2E qua gateway OK
- [x] `docker/mysql/init.sql` tạo sẵn 5 database
- [x] Sửa xung đột env var: gateway dùng `*_SERVICE_URI` (base) tách khỏi `*_SERVICE_URL` (có path)
- [x] `README.md` — sơ đồ kiến trúc (mermaid), tech stack, điểm kỹ thuật nổi bật, hướng dẫn chạy, roadmap
- [x] **CI — GitHub Actions**: build + `mvn test` cả 6 service khi push/PR, badge trong README — `.github/workflows/ci.yml` (matrix 6 service, `./mvnw verify` JDK 21); run đầu tiên xanh 6/6 job
- [x] **Postman collection** — full luồng: đăng ký → login → xem phim → giữ ghế → thanh toán (MoMo sandbox) → vé QR. Export vào repo. — `postman/` (collection v2.1.0 + environment + README); verify bằng Newman trên stack thật: 31/31 assertion PASS
- [ ] **Dọn secret default hardcode** trong `application.yaml`/`.yml` đã commit (`JWT_SECRET`, `GATEWAY_SECRET`, `INTERNAL_SECRET` đang có giá trị mặc định lộ trong git) → đổi thành placeholder vô hại hoặc bỏ default để fail-fast

### Giai đoạn B — Deploy

- [ ] Chốt host (Oracle Cloud hoặc PC + tunnel) và tạo instance / cài `cloudflared`
- [ ] Cài Docker + Docker Compose plugin trên host
- [ ] Tạo `.env` production thật trên host (secret mạnh, **KHÔNG commit**) — sinh bằng `openssl rand -hex 32`
- [ ] `git clone` + `docker compose up -d --build` trên host (ARM: build trực tiếp trên instance)
- [ ] Reverse proxy (Caddy/Nginx) + HTTPS cho domain/subdomain → `api-gateway:8080`
- [ ] Cập nhật `MOMO_NOTIFY_URL` = `https://<domain thật>/api/v1/payment/momo/ipn`
- [ ] Verify E2E qua link công khai (chạy Postman collection trỏ vào domain thật)
- [ ] (tùy) CD: GitHub Actions SSH vào host, `git pull` + `docker compose up -d --build` khi merge vào `main`

### Giai đoạn C — Hoàn thiện trình bày

- [ ] README: thêm **link live** + link Swagger + ảnh/GIF demo luồng đặt vé
- [ ] Sequence diagram luồng thanh toán MoMo (create → IPN → confirm qua RabbitMQ → vé QR)
- [ ] (tùy) Prometheus + Grafana dashboard, hoặc log tập trung (Loki)
- [ ] Chuẩn hóa `.env.example` khớp hoàn toàn với biến compose thực dùng

### Giai đoạn D — Thêm tính năng khác biệt (CHỈ SAU KHI đã deploy)

Chọn 1–2 mục mở ra kỹ năng KHÁC (không phải thêm CRUD):

- [ ] **Voucher / mã giảm giá** — logic tính giảm giá + xử lý đồng thời khi redeem + tích hợp vào luồng `Booking`
- [ ] **Dashboard doanh thu / tỷ lệ lấp đầy** — query tổng hợp, một kiểu việc khác
- [ ] **Phân quyền nhân viên rạp (RBAC)** — chiều sâu về authorization

KHÔNG làm: hạng thành viên, referral, đặt vé nhóm, xuất Excel, giá vé linh hoạt (ít tín hiệu, nhiều việc).

---

## 4. Trạng thái hiện tại

**Cập nhật:** 2026-09-04 (cuối phiên).

- Đang ở: **cuối Giai đoạn A**. Đã container hóa xong, cả stack chạy bằng `docker compose up -d --build` (9/9 container healthy, E2E qua gateway OK). **CI GitHub Actions đã xanh.**
- **Nợ kỹ thuật "còn treo" từ các phiên trước đã dọn sạch** (phiên 2026-09-04): gộp 2 filter catalog chồng nhau, externalize CORS origin gateway, unit test `holdSeats` + `processIpn` (booking 32 test / payment 25 test).
- **Việc TIẾP THEO ngay:** Dọn secret default hardcode → chuyển sang Giai đoạn B (chốt host + deploy). (CI + Postman collection đã xong.)
- Chưa chốt host. Cần bạn quyết: có thẻ để xác minh Oracle Cloud không? Nếu có → Oracle. Nếu không → PC + Cloudflare Tunnel.

---

## 5. Nhật ký theo phiên

### Phiên 2026-09-04

Chi tiết đầy đủ ở `SESSION_SUMMARY_2026-09-04.md`. Tóm tắt: **dọn sạch nợ kỹ thuật "còn treo"** liệt kê ở cuối phiên trước.

- Rà soát code từng mục nợ. `.env.example` URL inconsistency → thực ra đã fix ở phiên 2026-09-03 (xoá memory tương ứng).
- **Gộp filter catalog**: xoá `InternalApiFilter` (bị `ServiceAccessFilter` bao trùm hoàn toàn).
- **Externalize CORS origin gateway**: `@Value("${app.cors.allowed-origins}")` ← env `CORS_ALLOWED_ORIGINS` (compose + `.env.example` cập nhật kèm).
- **Unit test bổ sung**: `holdSeats` + `validateSeatSelectionRules` (11 test: rule COUPLE, ghế cô lập, lock Redis, ghế bảo trì/không thuộc phòng...) → `BookingServiceImplTest` 32 test. `processIpn` MoMo (11 test: chữ ký, idempotent IPN lặp, PAYMENT_REVIEW/REFUND_REQUIRED, các nhánh máy trạng thái) → `MomoServiceImplTest` 25 test.
- `mvn test` cả 4 service liên quan BUILD SUCCESS. **Chưa commit** (chờ user xác nhận).

**Phiên 2 cùng ngày — CI GitHub Actions:** `.github/workflows/ci.yml` (matrix 6 service, `./mvnw verify` JDK 21, cache `~/.m2`, upload surefire-reports) + đặt bit thực thi 6 file `mvnw` + badge README. Push → **run đầu tiên xanh 6/6 job**.

**Còn dang dở:** Postman collection · dọn secret default hardcode · chốt host + deploy (Giai đoạn B).

### Phiên 2026-09-03

Chi tiết đầy đủ ở `SESSION_SUMMARY_2026-09-03.md`. Tóm tắt:

**Nửa đầu — củng cố nền tảng kỹ thuật (Giai đoạn 4 checklist: 4/17 → 10/17), 11 commit đã push:**
1. Dọn dead code + gộp route gateway trùng (`40e04ef`)
2. Constant-time so sánh secret nội bộ (`e98627c`)
3. Externalize URL hardcode (`e32ffae`)
4. `System.out/err` → SLF4J cả 6 service (`ade5e68`)
5. Unit test `BookingServiceImpl` (21) + `MomoServiceImpl` (14) (`8ea11ed`)
6. Swagger/OpenAPI 5 service backend (`d974194`)
7. Actuator health check cả 6 service (`31d831e`)
8. Resilience4j circuit breaker + retry cho REST nội bộ (`631ceaa`)
9. Session summary (`bb46326`)
10. **Container hóa đầy đủ + docker-compose full stack + README** (`2fe58e3`)

**Nửa sau — bàn hướng đi + container hóa:**
- Chốt mục tiêu: portfolio deploy để xin việc → **deploy trước, không làm Giai đoạn 2/3 vội**.
- Phân tích phương án host free ($0): Oracle Cloud Always Free ARM (ưu tiên) vs PC + Cloudflare Tunnel.
- Quyết định: KHÔNG làm frontend, dùng Swagger + Postman.
- Làm xong toàn bộ container hóa (6 Dockerfile + compose full stack + init.sql + sửa env), viết README, verify chạy thật.

**Còn dang dở (đầu phiên sau làm tiếp):**
- CI GitHub Actions
- Postman collection
- Dọn secret default hardcode trong yaml
- Chốt host + deploy (Giai đoạn B)

**Ghi chú kỹ thuật phát hiện trong phiên:**
- ~~catalog-service có 2 filter chồng nhau (`ServiceAccessFilter` + `InternalApiFilter`) — nên gộp.~~ → đã gộp (phiên 2026-09-04).
- Khi gửi email với credential Gmail giả, notification-service treo → caller timeout (đã bắt lỗi, không chặn đăng ký). Deploy thật cần Gmail App Password thật.
- `eclipse-temurin:21-jre-alpine` + `wget` (busybox) dùng cho healthcheck trong compose — OK.
