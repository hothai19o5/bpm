/**
 * Sở hữu form definition/version, schema, UI schema và kiểm tra quyền trường.
 *
 * <p>Khi đến Phase 3: api nhận/trả schema DTO; application điều phối publish;
 * domain giữ quy tắc version bất biến/quyền trường; adapter gọi validator/lưu DB.
 * Cổng validation trả lỗi field/element bằng kiểu Java của ứng dụng.
 *
 * <p>Không sở hữu dữ liệu hồ sơ đã điền hoặc tự complete task.
 * P1.3 chưa chọn JSON Schema validator và chưa tạo FormService rỗng.
 */
package com.x.bpm.form;
