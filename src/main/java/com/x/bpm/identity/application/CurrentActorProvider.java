package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.ActorContext;

/** Cổng application; implementation nằm trong identity.adapter.security. */
public interface CurrentActorProvider {
    /** Thiếu principal ứng dụng đã xác thực thì ném UNAUTHENTICATED. */
    ActorContext requireCurrentActor();
}
