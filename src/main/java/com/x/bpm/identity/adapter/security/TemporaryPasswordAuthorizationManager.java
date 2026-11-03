package com.x.bpm.identity.adapter.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Chặn API nghiệp vụ cho đến khi tài khoản đổi mật khẩu tạm. */
@Component
public class TemporaryPasswordAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {
    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        var current = authentication.get();
        boolean granted = current != null && current.isAuthenticated()
                && current.getPrincipal() instanceof AuthenticatedAccountPrincipal principal
                && !principal.account().mustChangePassword();
        return new AuthorizationDecision(granted);
    }
}
