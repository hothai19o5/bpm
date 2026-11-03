package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.identity.application.AuthenticatedAccount;
import com.x.bpm.identity.application.CurrentAccountProvider;
import com.x.bpm.identity.domain.ActorContext;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Adapter duy nhất đọc Spring Security context và chỉ chấp nhận principal ứng dụng.
 */
@Component
public class SpringSecurityCurrentActorProvider
        implements CurrentActorProvider, CurrentAccountProvider {

    @Override
    public ActorContext requireCurrentActor() {
        return requireCurrentAccount().actorContext();
    }

    @Override
    public AuthenticatedAccount requireCurrentAccount() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof AuthenticatedAccountPrincipal principal)) {
            throw new ApplicationException(ErrorCode.UNAUTHENTICATED,
                    "Yêu cầu chưa có danh tính hợp lệ.");
        }
        return principal.account();
    }
}
