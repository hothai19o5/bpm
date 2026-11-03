/**
 * Sở hữu danh tính, trạng thái tài khoản, JWT đăng nhập và quyền hệ thống.
 *
 * <p>P1.3: ActorContext là dữ liệu danh tính tin cậy; CurrentActorProvider là
 * cổng để use case lấy người gọi; adapter.security nối với Spring Security.
 * AccessTokenIssuer phát JWT HS256; security kiểm tra Bearer token stateless.
 * P2.1b bổ sung domain model, persistence ports/adapters và password hashing.
 * P2.1c nối login, sid/ver, identity hiện tại, /me và gate mật khẩu tạm.
 * P2.1d–e tiếp tục refresh/logout/đổi mật khẩu và thu hồi;
 * API quản trị tạo tài khoản thuộc P2.2.
 *
 * <p>Module khác dùng ActorContext/CurrentActorProvider; không đọc security
 * context hoặc bảng tài khoản trực tiếp. Client không được tự gửi actorId/quyền.
 */
package com.x.bpm.identity;
