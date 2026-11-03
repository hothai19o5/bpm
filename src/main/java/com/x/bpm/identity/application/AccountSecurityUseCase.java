package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Thao tác nội bộ để P2.2/P2.3 nối API quản trị sau khi có policy quyền. */
public final class AccountSecurityUseCase {
    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final PasswordHashPort passwordHashes;
    private final PasswordPolicy passwordPolicy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public AccountSecurityUseCase(AuthAccountPort accounts, AuthGrantPort grants,
            PasswordHashPort passwordHashes, PasswordPolicy passwordPolicy,
            TransactionRunner transactions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.passwordHashes = Objects.requireNonNull(passwordHashes, "passwordHashes");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void resetPassword(UUID userId, String temporaryPassword) {
        mutate(userId, "password-reset", account -> {
            if (!passwordPolicy.violations(temporaryPassword, account.username()).isEmpty()
                    || passwordHashes.matches(temporaryPassword, account.passwordHash())) {
                throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                        "Mật khẩu tạm không đáp ứng chính sách.");
            }
            return account.resetPasswordHash(passwordHashes.hash(temporaryPassword));
        });
    }

    public void deactivate(UUID userId) {
        mutate(userId, "account-deactivated", account -> account.deactivate());
    }

    public void reactivate(UUID userId) {
        transactions.required(() -> {
            var account = requireLockedAccount(userId);
            accounts.save(account.reactivate());
            return null;
        });
    }

    public void revokeAllSessions(UUID userId) {
        mutate(userId, "sessions-revoked", account -> account.revokeAllSessions());
    }

    private void mutate(UUID userId, String reason,
            UnaryOperator<UserAccount> mutation) {
        Objects.requireNonNull(userId, "userId");
        transactions.required(() -> {
            var account = requireLockedAccount(userId);
            var changed = accounts.save(mutation.apply(account));
            GrantRevocationSupport.revokeAll(grants, changed.id(), Instant.now(clock), reason);
            return null;
        });
    }

    private UserAccount requireLockedAccount(UUID userId) {
        return accounts.findByIdForUpdate(Objects.requireNonNull(userId, "userId"))
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy tài khoản."));
    }
}
