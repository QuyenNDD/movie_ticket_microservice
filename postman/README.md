# Postman — Movie Ticket Microservices

Bộ collection chạy trọn luồng đặt vé qua **api-gateway** (`http://localhost:8080`):

> đăng ký → đăng nhập → xem phim → xem sơ đồ ghế → giữ ghế → thanh toán (giả lập hoặc MoMo sandbox) → chờ `PAID` → lấy vé QR → soát vé → hủy + hoàn tiền

| File | Mô tả |
|---|---|
| `movie-ticket.postman_collection.json` | Collection chính (5 folder, có test script tự chuyền biến). |
| `movie-ticket-local.postman_environment.json` | Environment mẫu cho stack chạy bằng `docker compose` (chỉ chứa `baseUrl` + credential). |

Collection **tự chứa** mọi biến mặc định — environment chỉ để tiện chỉnh `baseUrl` / mật khẩu mà không sửa collection.

## Chạy bằng Postman (GUI)

1. `File → Import` cả 2 file trên.
2. Chọn environment **movie-ticket · local**.
3. Bật cả stack: `docker compose up -d` ở thư mục gốc repo.
4. **Chạy theo thứ tự folder** bằng *Collection Runner* (nút ▶ trên collection), hoặc gửi từng request từ trên xuống.

## Chạy bằng Newman (CLI)

```bash
npx newman run postman/movie-ticket.postman_collection.json \
  -e postman/movie-ticket-local.postman_environment.json \
  --delay-request 300
```

`--delay-request` giúp bước *Poll booking until PAID* (đợi pipeline RabbitMQ chuyển booking sang `PAID`) đỡ phải lặp nhiều lần.

## Một bước thủ công: nâng quyền ADMIN

API `POST /auth/register` **luôn tạo role `USER`** — không có cách tự đăng ký ADMIN. Folder `0 · Admin — seed data` (tạo rạp/phòng/phim/suất chiếu) cần token ADMIN.

Sau khi chạy request **`Register admin`** lần đầu, xem collection variable `runId` rồi chạy:

```bash
docker exec -i mysql-db mysql -uroot -p1234 auth_db \
  -e "UPDATE users SET role='ADMIN' WHERE user_name='pmt_admin_<runId>';"
```

(`1234` là `MYSQL_ROOT_PASSWORD` mặc định trong `.env` — đổi cho khớp nếu bạn set khác.)

Rồi chạy lại **`Login admin`** để lấy token có quyền ADMIN, tiếp tục các request còn lại của folder 0.

> Nếu DB đã có sẵn phim + suất chiếu, có thể **bỏ qua folder 0** và set thủ công collection variable `showtimeId`. Folder 1–4 sẽ chạy trọn vẹn.

## Thanh toán: 2 lựa chọn trong folder 3

| Request | Khi nào dùng |
|---|---|
| **Simulate payment success (dev)** — `POST /payment/momo/test/{bookingId}/success` | Mặc định. Bỏ qua MoMo nhưng vẫn chạy đúng pipeline confirm qua RabbitMQ → booking chuyển `PAID`, sinh vé QR, gửi email. |
| **(Optional) Create real MoMo QR** — `POST /payment/momo/create` | Khi muốn test MoMo sandbox thật: cần `MOMO_*` trong `.env` và `MOMO_NOTIFY_URL` là URL công khai (ngrok / domain). Trả `payUrl` để mở trên trình duyệt; MoMo gọi ngược IPN về `/payment/momo/ipn`. |

## Ghi chú

- Auth mặc định của collection: `Bearer {{userToken}}`. Request công khai (`register`, `login`, `GET /catalog/...`) override thành *no-auth*; request quản trị override thành `Bearer {{adminToken}}`.
- Gateway có rate-limit cơ bản — chạy Runner với `--delay-request` nếu gặp `429`.
- Booking chuyển `PAID` **bất đồng bộ**. Request *Poll booking until PAID* tự lặp tối đa 20 lần (chỉ trong Runner/Newman). Gửi lẻ thì bấm Send lại vài lần.
- `Cancel booking + auto refund` trên giao dịch **giả lập** sẽ cho `refundStatus = FAILED` (transId `TEST_...` không hoàn tiền MoMo thật được) — đúng thiết kế.
