# Tóm tắt công việc — 2026-09-03

> Ghi lại nội dung đã trao đổi/thực hiện trong phiên làm việc này với Claude Code cho dự án `movie_ticket_microservice`. Tiếp nối `SESSION_SUMMARY_2026-08-28.md` (phiên 4).

## 0. Định hướng phiên

Đầu phiên thống nhất: Giai đoạn 1 (MVP lõi) coi như đóng (39/40, 3 mục còn lại bị chặn bởi credential/hạ tầng ngoài). **Không nhảy sang Giai đoạn 2** mà củng cố nền tảng kỹ thuật (Giai đoạn 4) trước, theo thứ tự: dọn nợ kỹ thuật nhỏ → unit test logic nghiệp vụ rủi ro cao → Swagger → Actuator → Resilience4j.

Kết thúc phiên: **Giai đoạn 4 tăng từ 4/17 → 9/17**. Đã push 9 commit lên `origin/main`.

---

## 1. Dọn nợ kỹ thuật nhỏ (4 commit)

Các điểm được phát hiện ở bước "kiểm chứng checklist" phiên 2026-08-28, phiên này xử lý dứt điểm.

### 1.1 `40e04ef` chore: dọn dead code + route gateway trùng lặp
- Xóa dead code: `RouteValidator` (api-gateway), `BookingProcessService` + `TicketEmailMessage` (booking-service, logic comment toàn bộ), `EmailListenerService` (notification-service, class rỗng đã comment).
- Xóa Feign client `CatalogClient` không dùng ở booking-service (chỉ có import lẻ ở `BookingServiceImpl`, không được inject) → gỡ luôn `@EnableFeignClients`, dependency `spring-cloud-starter-openfeign` và BOM `spring-cloud-dependencies` khỏi `booking-service/pom.xml`.
- Gộp 2 route trùng nhau trong `api-gateway/application.yaml` (`catalog-service-protected-write` / `catalog-service-user-protected` — giống hệt nhau).

### 1.2 `e98627c` security: so sánh secret nội bộ theo thời gian hằng số
- Thay `String.equals` bằng `MessageDigest.isEqual` khi kiểm tra `X-Internal-Secret` / `X-Gateway-Secret` ở 5 filter chặn API nội bộ (booking `ServiceAccessFilter`, payment `ServiceAccessFilter`, catalog `ServiceAccessFilter` + `InternalApiFilter`, notification `InternalAccessFilter`) — tránh timing attack. Thêm helper `secretMatches` xử lý null an toàn.

### 1.3 `e32ffae` refactor: externalize URL booking-service bị hardcode
- `BOOKING_SERVICE_BASE_URL = "http://localhost:8082/..."` (hằng số) trong `payment-service/MomoServiceImpl` và `catalog-service/RoomServiceImpl` → property `app.booking-service-url` đọc từ env `BOOKING_SERVICE_URL` (đã có sẵn trong `.env.example`), có default cho dev.

### 1.4 `ade5e68` refactor: thay System.out/err.println bằng SLF4J
- Chuyển toàn bộ log rải rác dùng `System.out`/`System.err` sang logger SLF4J (`@Slf4j` Lombok, hoặc `LoggerFactory` ở lớp không dùng Lombok) trên cả 6 service.
- Log lỗi → `log.error`; log tiến trình → `log.info`. Banner debug ở `AuthenticationFilter` và dump raw signature MoMo hạ xuống `log.debug`. Dùng placeholder `{}`.

### 1.5 `f89614f` docs — tick mục "dọn dead code" trong `FEATURE_CHECKLIST.md`.

---

## 2. `8ea11ed` test: unit test cho `BookingServiceImpl` và `MomoServiceImpl`

Unit test Mockito thuần (không cần hạ tầng), tập trung nhánh rủi ro cao nhất (tiền + đồng thời + state machine + bảo mật).

- **`BookingServiceImplTest`** (21 test): `confirmPayment` (not found / sai chủ / idempotent khi đã PAID / booking CANCELLED / hết hạn giữ chỗ → tự CANCELLED / happy path sinh 1 vé QR/ghế), `cancelBooking` (PENDING không hoàn tiền → `NOT_APPLICABLE` / PAID + suất chiếu đã bắt đầu → chặn / auto-refund SUCCESS → `COMPLETED` / bị từ chối → `FAILED` / mất kết nối payment-service → giữ `PENDING`), `getMyBookings` (tính `expiresInSeconds`), `getTickets` + `checkInTicket` (nhánh bảo mật/trạng thái).
- **`MomoServiceImplTest`** (14 test): `refundPayment` (not found / chưa SUCCESS / idempotent khi đã hoàn / transId test không hợp lệ → `FAILED` / MoMo chấp nhận → `SUCCESS` + lưu `momoRefundTransId` / MoMo từ chối → `FAILED` / lỗi mạng → `FAILED`), `handleBookingConfirmResult` (payment null → im lặng / đã SUCCESS → no-op / confirm fail → giữ trạng thái + set `lastError` / confirm ok → SUCCESS + set `paidAt`), `confirmBookingAndMarkSuccess` (dưới giới hạn retry → publish / chạm giới hạn → `PAYMENT_REVIEW` không publish / publish lỗi → `lastError`).
- **Chưa cover** (ghi nhận để làm sau): `holdSeats` + toàn bộ logic validate ghế (`validateSeatSelectionRules`, ghế cô lập, rule COUPLE); `processIpn` của MoMo (chữ ký, `markPaymentReview`/`markRefundRequired`).

---

## 3. `d974194` feat: tài liệu API tự động (Swagger/OpenAPI)

- 5 service backend (auth, catalog, booking, payment, notification): thêm `springdoc-openapi-starter-webmvc-ui:2.8.9` + `OpenApiConfig` (title/version/mô tả tiếng Việt + security scheme `bearerAuth` JWT).
- Swagger UI: `http://localhost:<8081-8085>/swagger-ui.html` · Spec JSON: `/v3/api-docs` — gọi trực tiếp vào cổng service, **không qua gateway**.
- auth-service: thêm `/v3/api-docs/**`, `/swagger-ui/**` vào `permitAll` của `SecurityConfig`.
- Kiểm chứng runtime: catalog (35 paths) / auth (10 paths) / booking (9 paths) — UI trả 200, các API nghiệp vụ vẫn bị filter/security chặn (401/403), `/actuator/env` trả 404.
- **api-gateway chưa gộp docs** (aggregation) — bỏ qua vì SCG + springdoc phức tạp và có sẵn vấn đề env-var URL không nhất quán ở gateway (xem mục 6).

---

## 4. `31d831e` feat: health check endpoint (Spring Boot Actuator)

- Cả 6 service: `spring-boot-starter-actuator`, chỉ expose `health,info` (KHÔNG expose env/beans/mappings...). `management.endpoint.health.show-details: always` + `probes.enabled: true` → có thêm `/actuator/health/liveness` và `/readiness` (sẵn cho Docker/K8s).
- auth-service: `/actuator/health/**`, `/actuator/info` vào `permitAll`.
- api-gateway: tắt `spring.cloud.discovery.client.health-indicator`/`composite-indicator` (chưa dùng service discovery) để health không lẫn trạng thái `UNKNOWN`.
- Kiểm chứng runtime: catalog/auth/booking/gateway `/actuator/health` = `UP`; booking hiển thị component `db` + `redis` + `rabbit`.
- Tiện tay xóa block `feign:` chết trong `booking-service/application.yml`.

---

## 5. `631ceaa` feat: circuit breaker + retry + timeout cho gọi REST nội bộ (Resilience4j)

- 4 service gọi REST nội bộ (auth, catalog, booking, payment): thêm `resilience4j-spring-boot3:2.3.0` + `spring-boot-starter-aop`.
- **`ResilientHttpInterceptor`** (`ClientHttpRequestInterceptor` gắn vào `RestTemplate` qua `.additionalInterceptors(...)`), chỉ bọc lời gọi có path bắt đầu `/api/v1/...`:
  - **Timeout**: đã có sẵn từ phiên trước (connect 3s / read 5s ở `RestTemplateConfig` / `ModelMapperConfig`).
  - **Circuit breaker** instance `internal`: sliding-window COUNT 20, mở khi ≥50% lỗi (tối thiểu 10 call), `wait-duration-in-open-state` 15s, `register-health-indicator: false` (không để downstream DOWN làm service báo unhealthy).
  - **Retry** instance `internal`: **chỉ method GET** (idempotent — tránh gửi trùng POST tạo thông báo / gọi MoMo), 3 lần × 500ms, `ignore-exceptions: CallNotPermittedException` (không retry vô ích khi mạch đã mở).
  - Lời gọi ra MoMo (payment) đi qua cùng `RestTemplate` nhưng **không bị bọc** (path `/v2/gateway/...` ≠ `/api/v1/...`) — lỗi bên thứ ba không làm mở mạch lời gọi nội bộ.
- **`GlobalExceptionHandler`** booking / payment / catalog: map `ResourceAccessException` + `CallNotPermittedException` → **HTTP 503** (trước đây connection lỗi trả 400/500 gây hiểu nhầm là lỗi input).
- **Kiểm chứng runtime**: chạy booking-service trỏ `CATALOG_SERVICE_URL` vào cổng chết 9999, bắn 20 request tới `GET /api/v1/booking/showtimes/st-1/seats`:
  - 3 request đầu ~1.0s (retry 3 lần × 500ms), request 3 ~0.5s (đang chuyển trạng thái)
  - từ request 4: circuit breaker OPEN → trả 503 trong ~4ms (fail nhanh, không mở kết nối)
  - sau 15s → HALF_OPEN, 1 request thử kết nối thật (~1s), lỗi → OPEN lại

---

## 6. Đồng thời với mục 5: dọn test `contextLoads` mặc định

- Đánh `@Disabled` (kèm lý do tiếng Việt) cho cả **6 test `@SpringBootTest contextLoads`** mặc định — chúng cần MySQL/Redis/RabbitMQ + biến môi trường thật để khởi động context, xưa nay luôn đỏ.
- Kết quả: `mvn test` cả 6 service đều **BUILD SUCCESS** (booking 22 test / payment 15 test / 4 service còn lại chỉ có test bị skip). Đây là điều kiện cần cho mục CI/CD sau này.

---

## 7. Quy trình làm việc trong phiên

Mỗi hạng mục đều: sửa code → compile cả 6 service (JDK 21, `mvnw -o`) → với tính năng có thể verify runtime thì khởi động service local bằng docker-compose (mysql/redis/rabbitmq) + `spring-boot:run` và test thật bằng `curl` → chạy lại `mvn test` booking + payment → cập nhật `FEATURE_CHECKLIST.md` → commit riêng từng nhóm việc → **hỏi xác nhận trước khi push**.

Dùng `git add -p` để tách hunk khi 1 file dính nhiều nhóm commit (vd. `BookingServiceImpl.java`, `MomoServiceImpl.java`).

---

## Commit đã push lên `origin/main` (thứ tự thời gian)

1. `40e04ef` chore: dọn dead code và route gateway trùng lặp
2. `e98627c` security: so sánh secret nội bộ theo thời gian hằng số
3. `e32ffae` refactor: externalize URL booking-service bị hardcode
4. `ade5e68` refactor: thay System.out/err.println bằng SLF4J
5. `f89614f` docs: tick "dọn dead code" trong FEATURE_CHECKLIST
6. `8ea11ed` test: unit test cho BookingServiceImpl và MomoServiceImpl
7. `d974194` feat: tài liệu API tự động bằng Swagger/OpenAPI (springdoc)
8. `31d831e` feat: health check endpoint bằng Spring Boot Actuator
9. `631ceaa` feat: circuit breaker + retry cho gọi REST nội bộ (Resilience4j)

## File đã tạo/cập nhật trong phiên này (theo service)

- **Root**: `FEATURE_CHECKLIST.md` (tick + count Giai đoạn 4: 4→9/17), `SESSION_SUMMARY_2026-09-03.md` (file này).
- **api-gateway**: `pom.xml` (actuator); `application.yaml` (gộp route trùng, `management`, tắt discovery health-indicator); `AuthenticationFilter.java` (SLF4J); xóa `util/RouteValidator.java`; `ApiGatewayApplicationTests.java` (`@Disabled`).
- **auth-service**: `pom.xml` (springdoc, actuator, aop, resilience4j); `config/OpenApiConfig.java` (mới), `config/ResilientHttpInterceptor.java` (mới), `config/RestTemplateConfig.java` (gắn interceptor), `config/SecurityConfig.java` (permitAll swagger + actuator); `service/UserServiceImpl.java` (SLF4J); `application.yaml` (`management`, `resilience4j`); `AuthServiceApplicationTests.java` (`@Disabled`).
- **catalog-service**: `pom.xml` (springdoc, actuator, aop, resilience4j); `config/OpenApiConfig.java` (mới), `config/ResilientHttpInterceptor.java` (mới), `config/ModelMapperConfig.java` (gắn interceptor); `config/ServiceAccessFilter.java` + `config/InternalApiFilter.java` (constant-time secret); `service/RoomServiceImpl.java` (URL từ env + SLF4J), `service/MovieServiceImpl.java` + `service/CinemaServiceImpl.java` (SLF4J); `exception/GlobalExceptionHandler.java` (503); `application.yaml` (`app.booking-service-url`, `management`, `resilience4j`); `CatalogServiceApplicationTests.java` (`@Disabled`).
- **booking-service**: `pom.xml` (gỡ openfeign + BOM, thêm springdoc/actuator/aop/resilience4j); `BookingServiceApplication.java` (gỡ `@EnableFeignClients`); `config/OpenApiConfig.java` (mới), `config/ResilientHttpInterceptor.java` (mới), `config/ModelMapperConfig.java` (gắn interceptor), `config/ServiceAccessFilter.java` (constant-time secret); `service/BookingServiceImpl.java` (gỡ import Feign, SLF4J); `scheduler/BookingCleanupJob.java`, `publisher/BookingConfirmResultPublisher.java`, `listener/BookingConfirmRequestListener.java` (SLF4J); `exception/GlobalExceptionHandler.java` (503); `application.yml` (xóa block `feign`, thêm `management` + `resilience4j`); xóa `client/CatalogClient.java`, `service/BookingProcessService.java`, `message/TicketEmailMessage.java`; **thêm** `src/test/.../service/BookingServiceImplTest.java` (21 test); `BookingServiceApplicationTests.java` (`@Disabled`).
- **payment-service**: `pom.xml` (springdoc, actuator, aop, resilience4j); `config/OpenApiConfig.java` (mới), `config/ResilientHttpInterceptor.java` (mới), `config/RestTemplateConfig.java` (gắn interceptor), `config/ServiceAccessFilter.java` (constant-time secret); `service/MomoServiceImpl.java` (URL từ env, SLF4J), `controller/PaymentController.java` + `config/RabbitMQConfig.java` + `publisher/*` + `listener/BookingConfirmResultListener.java` (SLF4J); `exception/GlobalExceptionHandler.java` (503); `application.yaml` (`app.booking-service-url`, `management`, `resilience4j`); **thêm** `src/test/.../service/MomoServiceImplTest.java` (14 test); `PaymentServiceApplicationTests.java` (`@Disabled`).
- **notification-service**: `pom.xml` (springdoc, actuator); `config/OpenApiConfig.java` (mới), `config/InternalAccessFilter.java` (constant-time secret), `config/RabbitMQConfig.java` (SLF4J); `service/EmailServiceImpl.java` + `listener/BookingPaidEmailListener.java` (SLF4J); `application.yaml` (`management`); xóa `service/EmailListenerService.java`; `NotificationServiceApplicationTests.java` (`@Disabled`).

## Trạng thái môi trường cuối phiên

- Docker: đã `docker compose stop mysql redis rabbitmq` — 3 container ở trạng thái Stopped (không xoá). Trong phiên có tạo thêm DB `auth_db`, `booking_db`, `payment_db`, `notification_db` trong container `mysql-db` (trước chỉ có `catalog_db` mặc định).
- Không còn service Java nào chạy local (đã kill hết cổng 8080-8085).
- `mvn test` cả 6 service: BUILD SUCCESS.

## Việc tiếp theo (chưa thực hiện)

### Giai đoạn 4 còn lại (8 mục)
- **Integration test luồng đặt vé end-to-end** — giờ đã có nền: `mvn test` xanh, có thể dùng Testcontainers.
- **CI/CD pipeline** (GitHub Actions: build + `mvn test` 6 service).
- **Container hóa đầy đủ 6 service** trong `docker-compose.yml` (hiện chỉ có `api-gateway`) — cần viết `Dockerfile` cho 5 service còn lại.
- Logging tập trung (ELK/Loki), Metrics & alerting (Prometheus/Grafana).
- Quản lý secret tập trung (Vault/AWS Secrets Manager).
- Service discovery (Eureka/Consul) thay URL cứng.
- HTTPS/SSL cho production.

### Nợ kỹ thuật / vấn đề còn treo (phát hiện trong phiên, chưa xử lý)
- **catalog-service có 2 filter chồng nhau**: `ServiceAccessFilter` và `InternalApiFilter` cùng gác các endpoint giá nội bộ — nên gộp còn 1.
- **`.env.example` không nhất quán URL giữa các service**: `PAYMENT_SERVICE_URL` có đuôi `/api/v1/payment/momo`, trong khi api-gateway dùng các biến `*_SERVICE_URL` như base host (default `http://host.docker.internal:<port>`). Nếu gateway container nạp `.env` thật thì route sẽ hỏng. Cần chuẩn hoá: gateway dùng biến base riêng, hoặc sửa `.env.example`.
- **api-gateway chưa gộp Swagger** của các service (aggregation) — phụ thuộc việc chuẩn hoá URL ở trên.
- **`CorsConfig` (gateway)** hardcode danh sách origin FE — nên externalize.
- Unit test chưa cover `holdSeats` (validate ghế) và `processIpn` của MoMo.

### Giai đoạn 2 (kinh doanh) / Giai đoạn 3 (quản trị rạp)
- Chưa bắt đầu. Khi mở Giai đoạn 2, ưu tiên **Voucher/mã giảm giá** (gắn tự nhiên vào luồng `Booking` đã hoàn chỉnh), rồi loyalty points.

---

# Bổ sung — phiên 2 cùng ngày (2026-09-03): định hướng deploy + container hóa

## 8. Chốt mục tiêu và hướng đi

Trao đổi với user, làm rõ mục tiêu thật của dự án: **portfolio để đi xin việc, cần triển khai lên môi trường production hoặc gần giống production** (có link công khai chạy thật).

Kết luận đã thống nhất:
- **Deploy trước, KHÔNG làm Giai đoạn 2/3 vội.** Lý do: Giai đoạn 1 gần xong, phần khó nhất đã làm; giá trị biên của việc thêm tính năng đang giảm; một repo không deploy = vô hình với nhà tuyển dụng; deploy ép đối mặt đúng các câu hỏi phỏng vấn (service discovery, secret, xử lý downstream chết, HTTPS...).
- **KHÔNG làm frontend** — dùng Swagger + Postman collection + README walkthrough làm mặt demo.
- **KHÔNG làm Kubernetes** — overkill, tốn tiền.
- Host: 2 phương án $0 — **Oracle Cloud Always Free ARM** (24GB RAM free vĩnh viễn, cần thẻ xác minh) ưu tiên 1; **PC + Cloudflare Tunnel** dự phòng. Chưa chốt.
- Sau khi deploy xong mới chọn 1–2 mục Giai đoạn 2/3 mở ra kỹ năng khác: Voucher, dashboard doanh thu, hoặc RBAC nhân viên.

→ Toàn bộ lộ trình + ô tick tiến độ được ghi vào file mới **`DEPLOYMENT_ROADMAP.md`** (đọc file này đầu mỗi phiên).

## 9. `2fe58e3` feat: container hóa đầy đủ + docker-compose full stack + README

- **6 `Dockerfile` multi-stage**: build `maven:3.9-eclipse-temurin-21` → runtime `eclipse-temurin:21-jre-alpine`, chạy non-root (user `app`), `-XX:MaxRAMPercentage=75`, `COPY --from=build /app/target/*.jar`. Layer cache deps qua `dependency:go-offline`. Kèm `.dockerignore` (loại `target/`). Image ~370–440MB/service.
- **`docker-compose.yml` viết lại hoàn toàn**: `name: movie-ticket`, anchor `x-service-common` cho `depends_on`/`restart`/`networks`. 6 service + `mysql:8.0` + `redis:7-alpine` + `rabbitmq:3-management-alpine`. Healthcheck cho tất cả + `depends_on: {condition: service_healthy}` (infra → service → gateway). URL nội bộ hardcode theo DNS tên container. `JPA_SHOW_SQL=false`. Chỉ expose **8080** (gateway) + **15672** (RabbitMQ UI). Volume `mysql-data`.
- **`docker/mysql/init.sql`**: tạo sẵn `auth_db`, `catalog_db`, `booking_db`, `payment_db`, `notification_db` (utf8mb4).
- **Sửa xung đột env var** (nợ kỹ thuật cũ): `api-gateway/application.yaml` các route đổi `${AUTH_SERVICE_URL:...}` → `${AUTH_SERVICE_URI:...}` (base host, không path) — tách hẳn khỏi `*_SERVICE_URL` (có path) mà auth/catalog/booking/payment dùng để gọi REST nội bộ. Compose set cả 2 loại rõ ràng.
- **`.env.example` viết lại**: chỉ còn secret (MySQL pw, JWT/GATEWAY/INTERNAL secret, Cloudinary, MoMo, Mail). URL nội bộ do compose lo. Phần DB URL cho chạy-ngoài-docker để dạng comment.
- **`README.md` mới**: mô tả, sơ đồ kiến trúc mermaid (flowchart services + infra + MoMo + RabbitMQ), bảng tech stack, mục "Key technical highlights" (seat locking, MoMo, async retry, e-tickets, defence in depth), hướng dẫn `docker compose up -d --build`, cách chạy 1 service ngoài docker, tests, repo layout, roadmap. **Viết tiếng Anh** (chuẩn portfolio).
- **Kiểm chứng runtime**: `docker compose up -d --build` → **9/9 container `healthy`**. Test E2E qua gateway `http://localhost:8080`: đăng ký (`userName`/`email`/`password`) → login (`userName`/`password`) → nhận JWT → `/api/v1/auth/me` (gateway verify JWT + inject `X-User-Id`) → `/api/v1/booking/my-bookings` (JWT + gateway secret xuyên container) → list phim công khai — tất cả 200; route không token → 401. Log các service sạch (chỉ 1 lỗi email do credential Gmail giả — đã bắt, không chặn đăng ký).
- Sau verify: `docker compose down -v` (xóa volume test).

## 10. File tạo/sửa trong phiên 2

- **Mới**: `DEPLOYMENT_ROADMAP.md` (lộ trình + ô tick, đọc đầu mỗi phiên), `README.md`, `docker-compose.yml` (viết lại), `docker/mysql/init.sql`, 6 × `Dockerfile`, 6 × `.dockerignore`.
- **Sửa**: `api-gateway/src/main/resources/application.yaml` (`*_SERVICE_URI`), `.env.example` (viết lại), `FEATURE_CHECKLIST.md` (tick "container hóa", Giai đoạn 4: 9→10/17), `SESSION_SUMMARY_2026-09-03.md` (file này), `CLAUDE.md` (thêm dòng đọc `DEPLOYMENT_ROADMAP.md` đầu phiên).

## 11. Việc TIẾP THEO (đầu phiên sau, theo `DEPLOYMENT_ROADMAP.md` Giai đoạn A còn lại)

1. **CI — GitHub Actions**: workflow build + `mvn test` cả 6 service khi push/PR; thêm badge vào README.
2. **Postman collection**: full luồng đặt vé (đăng ký → login → phim → suất chiếu → giữ ghế → thanh toán MoMo sandbox → vé QR), export `.json` vào repo.
3. **Dọn secret default hardcode** trong các `application.yaml`/`.yml` đã commit (`JWT_SECRET`/`GATEWAY_SECRET`/`INTERNAL_SECRET` đang để giá trị hex thật làm default) → placeholder vô hại hoặc bỏ default.
4. Sang **Giai đoạn B**: hỏi user chốt host (Oracle Cloud cần thẻ / PC + Cloudflare Tunnel), rồi deploy.
