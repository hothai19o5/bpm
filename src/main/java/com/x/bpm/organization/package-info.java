/**
 * Sở hữu phòng ban dạng cây, thành viên, phòng ban chính và trưởng phòng.
 *
 * <p>Khi đến P2.2: api nhận DTO; application điều phối quản lý tổ chức;
 * domain giữ quy tắc cây/thành viên; adapter.persistence lưu dữ liệu.
 * Cổng tra cứu tổ chức trả ID/DTO ứng dụng, không trả JPA entity.
 *
 * <p>Không tự đổi assignee của task đang chạy khi đổi trưởng phòng.
 * Task/assignment dùng dữ liệu tổ chức theo chính sách đã chốt ở C-09.
 * P1.3 chỉ chốt ranh giới, chưa dựng CRUD hoặc repository chưa có use case.
 */
package com.x.bpm.organization;
