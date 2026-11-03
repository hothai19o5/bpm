# Hướng dẫn làm việc với repository

<!-- postman-cli:installation:start -->
Postman CLI is installed on this device, and can be used for API Engineering work.
<!-- postman-cli:installation:end -->

## Postman

- Mỗi khi thêm, sửa hoặc xóa API, cập nhật collection/environment Postman tương ứng trong cùng mốc triển khai.
- Dùng cấu trúc Git-native Postman v3 trong `postman/`; giữ auth và biến dùng chung ở scope collection/folder, không sao chép xuống từng request nếu có thể kế thừa.
- Không commit access token, refresh token, mật khẩu hoặc secret thật. Biến nhạy cảm phải để rỗng, đánh dấu `type: secret` và chỉ bật sau khi người dùng tự cấu hình local.
- Trước khi commit thay đổi Postman, chạy `postman collection lint`, `postman environment lint` và `postman workspace lint`; xử lý cả error lẫn warning liên quan.
