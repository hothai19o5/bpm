package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.UserAccount;
import java.util.Objects;

/** Dữ liệu application cần để tạo tài khoản; không để password hash lọt qua toString. */
public final class NewUserAccount {
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final AccountStatus status;
    private final boolean mustChangePassword;

    public NewUserAccount(String username, String displayName, String passwordHash,
            AccountStatus status, boolean mustChangePassword) {
        this.username = requireText(username, "username").strip();
        this.displayName = requireText(displayName, "displayName").strip();
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        this.mustChangePassword = mustChangePassword;
        UserAccount.normalizeUsername(this.username);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public AccountStatus status() {
        return status;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public String toString() {
        return "NewUserAccount[username=" + username + ", displayName=" + displayName
                + ", passwordHash=<redacted>, status=" + status + ", mustChangePassword="
                + mustChangePassword + "]";
    }
}
