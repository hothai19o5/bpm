package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Rotate refresh credential một lần; replay sẽ thu hồi toàn bộ login grant. */
public final class RefreshTokenUseCase {
    static final String REPLAY_REVOKE_REASON = "refresh-token-replay";
    private static final String INVALID_REFRESH_TOKEN =
            "Refresh token không hợp lệ hoặc đã hết hạn.";
    private static final String PASSWORD_CHANGE_REQUIRED =
            "Bạn phải đổi mật khẩu tạm trước khi tiếp tục.";

    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final RefreshTokenIssuer refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final TransactionRunner transactions;
    private final Clock clock;

    public RefreshTokenUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            RefreshTokenIssuer refreshTokens, AccessTokenIssuer accessTokens,
            TransactionRunner transactions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "refreshTokens");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public RefreshTokenResult refresh(RefreshTokenCommand command) {
        Objects.requireNonNull(command, "command");
        var tokenHash = refreshTokens.hash(command.refreshToken());
        var credential = grants.findCredentialByTokenHash(tokenHash)
                .orElseThrow(RefreshTokenUseCase::unauthenticated);
        var grant = grants.findGrantById(credential.grantId())
                .orElseThrow(RefreshTokenUseCase::unauthenticated);

        var outcome = transactions.requiresNew(
                () -> rotate(grant.userId(), grant.id(), credential.id()));
        if (outcome.rejection() != null) {
            throw switch (outcome.rejection()) {
                case UNAUTHENTICATED -> unauthenticated();
                case PASSWORD_CHANGE_REQUIRED -> new ApplicationException(ErrorCode.FORBIDDEN,
                        PASSWORD_CHANGE_REQUIRED);
            };
        }
        return outcome.result();
    }

    private RotationOutcome rotate(UUID userId, UUID grantId, UUID credentialId) {
        // Thứ tự khóa thống nhất cho refresh/logout/password/account mutation.
        var account = accounts.findByIdForUpdate(userId).orElse(null);
        if (account == null) {
            return RotationOutcome.rejected(Rejection.UNAUTHENTICATED);
        }
        var grant = grants.findGrantByIdForUpdate(grantId).orElse(null);
        if (grant == null) {
            return RotationOutcome.rejected(Rejection.UNAUTHENTICATED);
        }
        var credential = grants.findCredentialByIdForUpdate(credentialId).orElse(null);
        if (credential == null || !grant.userId().equals(account.id())
                || !credential.grantId().equals(grant.id())) {
            return RotationOutcome.rejected(Rejection.UNAUTHENTICATED);
        }

        var now = Instant.now(clock);
        if (credential.isConsumed()) {
            revokeReplay(grant, now);
            return RotationOutcome.rejected(Rejection.UNAUTHENTICATED);
        }
        if (!account.isActive() || !grant.isUsableAt(now) || !credential.isUsableAt(now)
                || account.securityVersion() != grant.securityVersion()) {
            return RotationOutcome.rejected(Rejection.UNAUTHENTICATED);
        }
        if (account.mustChangePassword()) {
            return RotationOutcome.rejected(Rejection.PASSWORD_CHANGE_REQUIRED);
        }

        grants.saveCredential(credential.consume(now));
        var nextRefresh = refreshTokens.issue();
        grants.createCredential(new NewRefreshCredential(grant.id(), nextRefresh.tokenHash(),
                grant.expiresAt()));
        var permissions = accounts.findCurrentPermissions(account.id());
        var nextAccess = accessTokens.issue(account.id(), grant.id(), account.securityVersion(),
                permissions);
        var result = new RefreshTokenResult(nextAccess, nextRefresh.rawToken(), grant.expiresAt(),
                false, new LoginResult.UserSummary(account.id(), account.username(),
                        account.displayName(), permissions));
        return RotationOutcome.success(result);
    }

    private void revokeReplay(LoginGrant grant, Instant now) {
        if (grant.revokedAt() == null) {
            grants.saveGrant(grant.revoke(now, REPLAY_REVOKE_REASON));
        }
    }

    private static ApplicationException unauthenticated() {
        return new ApplicationException(ErrorCode.UNAUTHENTICATED, INVALID_REFRESH_TOKEN);
    }

    private enum Rejection {
        UNAUTHENTICATED,
        PASSWORD_CHANGE_REQUIRED
    }

    private record RotationOutcome(RefreshTokenResult result, Rejection rejection) {
        private RotationOutcome {
            if ((result == null) == (rejection == null)) {
                throw new IllegalArgumentException("Exactly one rotation outcome is required");
            }
        }

        static RotationOutcome success(RefreshTokenResult result) {
            return new RotationOutcome(Objects.requireNonNull(result, "result"), null);
        }

        static RotationOutcome rejected(Rejection rejection) {
            return new RotationOutcome(null, Objects.requireNonNull(rejection, "rejection"));
        }
    }
}
