package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.domain.ActorContext;

/**
 * Adapter duy nhất của lát cắt mẫu được đọc Spring Security context.
 * CHƯA là bean; principal ứng dụng/kiểm tra ACTIVE hoàn chỉnh thuộc P2.1.
 */
public class SpringSecurityCurrentActorProvider implements CurrentActorProvider {

    @Override
    public ActorContext requireCurrentActor() {
        // TODO: Đọc JwtAuthenticationToken từ SecurityContextHolder, không nhận session.
        // TODO: sub là application user ID; đọc ACTIVE/quyền hiện tại từ identity
        // khi có DB ở P2.1/P2.2; JWT hợp lệ không chứng minh tài khoản còn ACTIVE.
        // Không suy diễn userId từ username hoặc nhận quyền do client tự khai.
        // TODO: Chỉ đăng ký bean sau khi có principal hợp lệ ở P2.1.
        throw new UnsupportedOperationException("TODO: SpringSecurityCurrentActorProvider.requireCurrentActor");
    }
}
