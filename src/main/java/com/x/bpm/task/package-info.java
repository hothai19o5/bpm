/**
 * Sở hữu use case inbox, candidate queue, claim, complete và ngoại lệ phân công.
 *
 * <p>Khi đến P1.5/Phase 5: api nhận action DTO; application kiểm tra người gọi,
 * quyền, revision và phối hợp engine; domain giữ quy tắc assignment/action;
 * adapter.flowable chuyển đổi task của engine sang DTO ứng dụng.
 *
 * <p>Không trả org.flowable.task.api.Task qua HTTP, không nhân đôi task runtime
 * bằng một bảng trạng thái riêng, không coi quyền Admin là quyền duyệt.
 * P1.3 chưa triển khai claim/complete hoặc chống lặp.
 */
package com.x.bpm.task;
