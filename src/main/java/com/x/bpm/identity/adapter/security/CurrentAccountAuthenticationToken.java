package com.x.bpm.identity.adapter.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Authentication không giữ raw JWT trong principal/credentials. */
final class CurrentAccountAuthenticationToken extends AbstractAuthenticationToken {
    private final AuthenticatedAccountPrincipal principal;

    CurrentAccountAuthenticationToken(AuthenticatedAccountPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedAccountPrincipal getPrincipal() {
        return principal;
    }
}
