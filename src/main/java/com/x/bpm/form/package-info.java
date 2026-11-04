/**
 * Sở hữu form definition/version, schema, UI schema và kiểm tra quyền trường.
 *
 * <p>P3.1: domain giữ hợp đồng bpm-form-v1; application tạo form/draft và đọc
 * version; API trả DTO snapshot, adapter lưu hai bảng form hiện có.
 * P3.2 bổ sung validator dữ liệu, quyền trường và read/patch service dùng chung;
 * caller cung cấp ngữ cảnh backend đã kiểm tra resource/task/action.
 * Vòng đời publish được bổ sung ở P3.5.
 *
 * <p>Không sở hữu dữ liệu hồ sơ đã điền hoặc tự complete task.
 * P1.3 chưa chọn JSON Schema validator và chưa tạo FormService rỗng.
 */
package com.x.bpm.form;
