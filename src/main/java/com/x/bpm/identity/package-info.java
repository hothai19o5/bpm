/**
 * Sở hữu danh tính, trạng thái tài khoản, JWT đăng nhập và quyền hệ thống.
 *
 * <p>P1.3: ActorContext là dữ liệu danh tính tin cậy; CurrentActorProvider là
 * cổng để use case lấy người gọi; adapter.security nối với Spring Security.
 * AccessTokenIssuer phát JWT HS256; security kiểm tra Bearer token stateless.
 * P2.1 mới nối login/refresh/thu hồi token và vòng đời tài khoản đầy đủ.
 *
 * <p>Module khác dùng ActorContext/CurrentActorProvider; không đọc security
 * context hoặc bảng tài khoản trực tiếp. Client không được tự gửi actorId/quyền.
 */
package com.x.bpm.identity;
