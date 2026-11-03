package com.x.bpm.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Thu hồi đúng login grant tìm được từ refresh token; thao tác có tính idempotent. */
public final class LogoutUseCase {
    static final String LOGOUT_REVOKE_REASON = "logout";

    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final RefreshTokenIssuer refreshTokens;
    private final TransactionRunner transactions;
    private final Clock clock;

    public LogoutUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            RefreshTokenIssuer refreshTokens, TransactionRunner transactions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void logout(RefreshTokenCommand command) {
        Objects.requireNonNull(command, "command");
        var tokenHash = refreshTokens.hash(command.refreshToken());
        var credential = grants.findCredentialByTokenHash(tokenHash).orElse(null);
        if (credential == null) {
            return;
        }
        var grant = grants.findGrantById(credential.grantId()).orElse(null);
        if (grant == null) {
            return;
        }
        transactions.required(() -> {
            revoke(grant.userId(), grant.id(), credential.id());
            return null;
        });
    }

    private void revoke(UUID userId, UUID grantId, UUID credentialId) {
        // Giữ cùng thứ tự khóa với refresh để hai luồng không để token sống lại.
        var account = accounts.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return;
        }
        var grant = grants.findGrantByIdForUpdate(grantId).orElse(null);
        if (grant == null) {
            return;
        }
        var credential = grants.findCredentialByIdForUpdate(credentialId).orElse(null);
        if (credential == null || !grant.userId().equals(account.id())
                || !credential.grantId().equals(grant.id()) || grant.revokedAt() != null) {
            return;
        }
        grants.saveGrant(grant.revoke(Instant.now(clock), LOGOUT_REVOKE_REASON));
    }
}
