# Hướng dẫn làm việc với repository

<!-- postman-cli:installation:start -->
Postman CLI is installed on this device, and can be used for API Engineering work.
<!-- postman-cli:installation:end -->

## Commit theo mốc triển khai

- Sau khi hoàn thành mỗi phase lớn hoặc mỗi bước nhỏ trong kế hoạch (ví dụ P1.3, P2.1), luôn nhắc người dùng commit các thay đổi trước khi chuyển sang bước tiếp theo.
- Khi nhắc, tóm tắt ngắn phạm vi thay đổi, nêu trạng thái kiểm tra và đề xuất một commit message phù hợp.
- Chỉ tự tạo commit khi người dùng yêu cầu rõ; việc nhắc commit không có nghĩa là được tự động commit hoặc push.
- Nếu mới hoàn thành phần code/cấu hình và còn chờ người dùng xác nhận build/test, ghi rõ trạng thái đó; không báo toàn bộ bước đã đạt.

## Build và test

- Người dùng tự chạy các lệnh build và test. Không tự chạy build/test trừ khi người dùng thay đổi chỉ dẫn này.
- Có thể đọc source và kiểm tra cấu hình tĩnh; cung cấp các lệnh build/test cần chạy cùng kết quả mong đợi.
