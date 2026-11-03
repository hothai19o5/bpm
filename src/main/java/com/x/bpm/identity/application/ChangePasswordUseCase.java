package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import com.x.bpm.shared.error.ValidationIssue;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;

/** Đổi mật khẩu của principal hiện tại và thu hồi mọi phiên trong một transaction. */
public final class ChangePasswordUseCase {
    static final String REVOKE_REASON = "password-changed";
    private static final String INVALID_CURRENT_PASSWORD = "Mật khẩu hiện tại không hợp lệ.";

    private final CurrentAccountProvider currentAccounts;
    private final AuthAccountPort accounts;
    private final AuthGrantPort grants;
    private final PasswordHashPort passwordHashes;
    private final PasswordPolicy passwordPolicy;
    private final TransactionRunner transactions;
    private final Clock clock;

    public ChangePasswordUseCase(CurrentAccountProvider currentAccounts, AuthAccountPort accounts,
            AuthGrantPort grants, PasswordHashPort passwordHashes, PasswordPolicy passwordPolicy,
            TransactionRunner transactions, Clock clock) {
        this.currentAccounts = Objects.requireNonNull(currentAccounts, "currentAccounts");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.grants = Objects.requireNonNull(grants, "grants");
        this.passwordHashes = Objects.requireNonNull(passwordHashes, "passwordHashes");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void changePassword(ChangePasswordCommand command) {
        Objects.requireNonNull(command, "command");
        var current = currentAccounts.requireCurrentAccount();
        transactions.required(() -> {
            var account = accounts.findByIdForUpdate(current.userId())
                    .filter(candidate -> candidate.isActive()
                            && candidate.securityVersion() == current.securityVersion())
                    .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED,
                            "Danh tính hiện tại không còn hợp lệ."));
            var now = Instant.now(clock);
            grants.findGrantByIdForUpdate(current.loginGrantId())
                    .filter(grant -> grant.userId().equals(account.id())
                            && grant.securityVersion() == account.securityVersion()
                            && grant.isUsableAt(now))
                    .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED,
                            "Danh tính hiện tại không còn hợp lệ."));
            if (!passwordHashes.matches(command.currentPassword(), account.passwordHash())) {
                throw new ApplicationException(ErrorCode.UNAUTHENTICATED,
                        INVALID_CURRENT_PASSWORD);
            }
            validateNewPassword(command.newPassword(), account.username(), account.passwordHash());
            var changed = accounts.save(account.changePasswordHash(
                    passwordHashes.hash(command.newPassword())));
            GrantRevocationSupport.revokeAll(grants, changed.id(), now, REVOKE_REASON);
            return null;
        });
    }

    private void validateNewPassword(String newPassword, String username,
            String currentPasswordHash) {
        var issues = new ArrayList<ValidationIssue>();
        passwordPolicy.violations(newPassword, username).stream()
                .sorted()
                .map(ChangePasswordUseCase::toIssue)
                .forEach(issues::add);
        if (newPassword != null && passwordHashes.matches(newPassword, currentPasswordHash)) {
            issues.add(new ValidationIssue("newPassword",
                    "Mật khẩu mới phải khác mật khẩu hiện tại.", null));
        }
        if (!issues.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Mật khẩu mới không đáp ứng chính sách.", issues);
        }
    }

    private static ValidationIssue toIssue(PasswordPolicy.Violation violation) {
        var message = switch (violation) {
            case MISSING -> "Mật khẩu mới là bắt buộc.";
            case TOO_SHORT -> "Mật khẩu mới phải có ít nhất 12 ký tự Unicode.";
            case TOO_LONG -> "Mật khẩu mới không được vượt quá 128 ký tự Unicode.";
            case CONTROL_CHARACTER -> "Mật khẩu mới không được chứa ký tự điều khiển.";
            case ONLY_WHITESPACE -> "Mật khẩu mới không được chỉ gồm khoảng trắng.";
            case MATCHES_USERNAME -> "Mật khẩu mới không được trùng tên đăng nhập.";
        };
        return new ValidationIssue("newPassword", message, null);
    }
}
