/**
 * Sở hữu hồ sơ, dữ liệu form đã điền, requester, revision và version đã ghim.
 *
 * <p>Khi đến Phase 5: api nhận DTO; application điều phối tạo/lưu/gửi hồ sơ;
 * domain giữ quy tắc nháp/trạng thái/quyền xem; adapter.persistence lưu dữ liệu.
 * Transaction gửi hồ sơ phải phối hợp dữ liệu ứng dụng với engine qua cổng.
 *
 * <p>Không dựng state machine riêng thay Flowable và không cho client tự đặt
 * requester, trạng thái cuối hoặc sửa toàn bộ dữ liệu sau khi gửi.
 * P5.1 đã nối draft API, Start Event field policy, pin schema/version và audit.
 * Submit/engine và xử lý task tiếp tục ở các bước P5.2 trở đi.
 */
package com.x.bpm.businessrecord;
