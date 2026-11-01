/**
 * Sở hữu thông báo trong ứng dụng và trạng thái đã đọc của người nhận.
 *
 * <p>Khi đến P6.6: api chỉ trả thông báo của người gọi; application điều phối
 * tạo/đọc; domain giữ quy tắc người nhận; adapter.persistence lưu dữ liệu.
 * Thông báo thành công phải gắn với kết quả transaction đã commit.
 *
 * <p>Không thêm email/browser push, không phát thông báo thành công cho rollback.
 * P1.3 chưa triển khai listener/outbox hoặc NotificationService rỗng.
 */
package com.x.bpm.notification;
