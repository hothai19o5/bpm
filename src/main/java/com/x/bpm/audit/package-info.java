/**
 * Sở hữu audit nghiệp vụ: actor, hành động, tài nguyên, thời điểm, kết quả.
 *
 * <p>Khi có use case ghi audit: application nhận sự kiện bằng DTO ứng dụng;
 * domain quy định dữ liệu được ghi; adapter.persistence lưu và tra cứu có quyền.
 * Không dùng application log hoặc correlation ID thay lịch sử nghiệp vụ.
 *
 * <p>Không ghi password/session/token hoặc toàn bộ form nhạy cảm; không tạo
 * bản ghi audit thành công nếu giao dịch nghiệp vụ rollback.
 * P1.3 chỉ dựng correlation/logging kỹ thuật ở shared, chưa dựng audit store.
 */
package com.x.bpm.audit;
