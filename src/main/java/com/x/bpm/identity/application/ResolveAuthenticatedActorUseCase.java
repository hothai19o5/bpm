package com.x.bpm.identity.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Đối chiếu sub/sid/ver với account, grant và quyền hiện tại trong DB. */
public final class ResolveAuthenticatedActorUseCase {
    private static final String INVALID_IDENTITY = "Bearer token không còn hiệu lực.";

    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ResolveAuthenticatedActorUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            TransactionRunner transactions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AuthenticatedAccount resolve(String subject, String sid, long tokenSecurityVersion) {
        if (tokenSecurityVersion < 0) {
            throw unauthenticated();
        }
        final UUID userId;
        final UUID grantId;
        try {
            userId = UUID.fromString(subject);
            grantId = UUID.fromString(sid);
            if (!userId.toString().equals(subject) || !grantId.toString().equals(sid)) {
                throw unauthenticated();
            }
        } catch (RuntimeException exception) {
            throw unauthenticated();
        }

        return transactions.required(() -> {
            var account = accounts.findById(userId).orElseThrow(
                    ResolveAuthenticatedActorUseCase::unauthenticated);
            var grant = grants.findGrantById(grantId).orElseThrow(
                    ResolveAuthenticatedActorUseCase::unauthenticated);
            var now = Instant.now(clock);
            if (!account.isActive() || !grant.userId().equals(account.id())
                    || !grant.isUsableAt(now)
                    || tokenSecurityVersion != account.securityVersion()
                    || tokenSecurityVersion != grant.securityVersion()) {
                throw unauthenticated();
            }
            var permissions = accounts.findCurrentPermissions(account.id());
            return new AuthenticatedAccount(account.id(), account.username(),
                    account.displayName(), permissions, account.mustChangePassword(), grant.id(),
                    account.securityVersion());
        });
    }

    private static ApplicationException unauthenticated() {
        return new ApplicationException(ErrorCode.UNAUTHENTICATED, INVALID_IDENTITY);
    }
}
