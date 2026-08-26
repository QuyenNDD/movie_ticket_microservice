# Quy tắc làm việc — movie_ticket_microservice

File này được đọc tự động vào đầu mỗi phiên làm việc với Claude Code. Đây là quy tắc bắt buộc, phải tuân thủ trong suốt phiên.

## 1. Đầu phiên
- Đọc file `SESSION_SUMMARY_<ngày gần nhất>.md` (nếu có) để nắm bối cảnh phiên trước đã làm gì, đang dở việc gì.
- Đọc `FEATURE_CHECKLIST.md` để biết chức năng nào đã xong (✅), chức năng nào đang thiếu (⬜).

## 2. Trong khi làm việc
- Khi hoàn thành xong một chức năng (code chạy được, không phải nửa vời):
  - Mở `FEATURE_CHECKLIST.md`, tick ô tương ứng từ `⬜` thành `✅`.
  - Nếu chức năng tạo entity DB mới đúng như ghi chú "→ Entity cần thêm" trong checklist, cập nhật luôn ghi chú đó (đánh dấu đã tạo).
- Không tick trước khi chức năng thực sự chạy được (không tick cho code stub/dở dang).

## 3. Cuối phiên (hoặc khi user yêu cầu tổng kết)
- Tự động ghi lại nội dung đã trao đổi/thực hiện trong phiên vào file `SESSION_SUMMARY_<YYYY-MM-DD>.md` (ngày hiện tại), theo đúng format của các file summary trước:
  - Các mục đã khảo sát/quyết định.
  - Các file đã tạo/sửa trong phiên.
  - Việc tiếp theo còn dang dở / chưa thực hiện.
- Nếu file summary của ngày đó đã tồn tại (nhiều phiên trong cùng ngày), bổ sung thêm mục mới vào cuối file thay vì ghi đè mất nội dung cũ.

## Ghi chú
- Ngôn ngữ trao đổi mặc định: tiếng Việt.
