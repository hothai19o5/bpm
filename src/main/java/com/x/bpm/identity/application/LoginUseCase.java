package com.x.bpm.identity.application;

import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Xác thực credentials và tạo grant/refresh/access token trong một transaction. */
public final class LoginUseCase {
    private static final String INVALID_CREDENTIALS = "Thông tin đăng nhập không hợp lệ.";

    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final PasswordHashPort passwordHashes;
    private final RefreshTokenIssuer refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    private final String dummyPasswordHash;

    public LoginUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            PasswordHashPort passwordHashes, RefreshTokenIssuer refreshTokens,
            AccessTokenIssuer accessTokens, TransactionRunner transactions, Clock clock,
            Duration refreshTokenTtl, String dummyPasswordHash) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.passwordHashes = Objects.requireNonNull(passwordHashes, "passwordHashes");
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl, "refreshTokenTtl");
        if (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("refreshTokenTtl must be positive");
        }
        if (dummyPasswordHash == null || dummyPasswordHash.isBlank()) {
            throw new IllegalArgumentException("dummyPasswordHash must not be blank");
        }
        this.dummyPasswordHash = dummyPasswordHash;
    }

    public LoginResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command");
        var candidate = accounts.findByUsername(command.username());
        var passwordMatches = passwordHashes.matches(command.password(),
                candidate.map(account -> account.passwordHash()).orElse(dummyPasswordHash));
        if (candidate.isEmpty() || !passwordMatches || !candidate.orElseThrow().isActive()) {
            throw unauthenticated();
        }

        var accountId = candidate.orElseThrow().id();
        return transactions.required(() -> {
            var account = accounts.findByIdForUpdate(accountId).orElseThrow(LoginUseCase::unauthenticated);
            if (!account.isActive()
                    || !passwordHashes.matches(command.password(), account.passwordHash())) {
                throw unauthenticated();
            }

            var now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
            var refreshExpiresAt = now.plus(refreshTokenTtl);
            var grant = grants.createGrant(new NewLoginGrant(account.id(),
                    account.securityVersion(), refreshExpiresAt));
            var refresh = refreshTokens.issue();
            grants.createCredential(new NewRefreshCredential(grant.id(), refresh.tokenHash(),
                    refreshExpiresAt));
            var permissions = accounts.findCurrentPermissions(account.id());
            var access = accessTokens.issue(account.id(), grant.id(), account.securityVersion(),
                    permissions);
            return new LoginResult(access, refresh.rawToken(), refreshExpiresAt,
                    account.mustChangePassword(), new LoginResult.UserSummary(account.id(),
                            account.username(), account.displayName(), permissions));
        });
    }

    private static ApplicationException unauthenticated() {
        return new ApplicationException(ErrorCode.UNAUTHENTICATED, INVALID_CREDENTIALS);
    }
}
