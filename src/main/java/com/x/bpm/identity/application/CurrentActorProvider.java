package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.ActorContext;

/** Cổng application; implementation nằm trong identity.adapter.security. */
public interface CurrentActorProvider {

    // TODO (adapter): Đọc danh tính JWT tin cậy; thiếu token/tài khoản inactive thì
    // ném UNAUTHENTICATED; tuyệt đối không trả anonymous hoặc actor giả.
    ActorContext requireCurrentActor();
}
