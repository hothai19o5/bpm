package com.x.bpm.identity.adapter.security;

import com.x.bpm.identity.application.AuthenticatedAccount;
import java.security.Principal;
import java.util.Objects;

/** Principal ứng dụng chứa identity đã đối chiếu với DB. */
public record AuthenticatedAccountPrincipal(AuthenticatedAccount account) implements Principal {
    public AuthenticatedAccountPrincipal {
        Objects.requireNonNull(account, "account");
    }

    @Override
    public String getName() {
        return account.userId().toString();
    }
}
