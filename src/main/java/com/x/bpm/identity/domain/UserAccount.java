package com.x.bpm.identity.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Tài khoản xác thực độc lập với JPA/Spring Security.
 * Password hash không xuất hiện trong {@link #toString()}.
 */
public final class UserAccount {
    private final UUID id;
    private final String username;
    private final String normalizedUsername;
    private final String displayName;
    private final String passwordHash;
    private final AccountStatus status;
    private final boolean mustChangePassword;
    private final long securityVersion;
    private final long revision;

    public UserAccount(UUID id, String username, String normalizedUsername, String displayName,
            String passwordHash, AccountStatus status, boolean mustChangePassword,
            long securityVersion, long revision) {
        this.id = Objects.requireNonNull(id, "id");
        this.username = requireText(username, "username").strip();
        this.normalizedUsername = requireText(normalizedUsername, "normalizedUsername");
        this.displayName = requireText(displayName, "displayName").strip();
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status, "status");
        if (!this.normalizedUsername.equals(normalizeUsername(this.username))) {
            throw new IllegalArgumentException("normalizedUsername does not match username");
        }
        if (securityVersion < 0 || revision < 0) {
            throw new IllegalArgumentException("securityVersion and revision must not be negative");
        }
        this.mustChangePassword = mustChangePassword;
        this.securityVersion = securityVersion;
        this.revision = revision;
    }

    public static String normalizeUsername(String username) {
        return requireText(username, "username").strip().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String normalizedUsername() {
        return normalizedUsername;
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

    public long securityVersion() {
        return securityVersion;
    }

    public long revision() {
        return revision;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public UserAccount changePasswordHash(String newPasswordHash) {
        return copy(requireText(newPasswordHash, "newPasswordHash"), status,
                false, nextSecurityVersion());
    }

    public UserAccount changeDisplayName(String newDisplayName) {
        return new UserAccount(id, username, normalizedUsername,
                requireText(newDisplayName, "newDisplayName").strip(), passwordHash, status,
                mustChangePassword, securityVersion, revision);
    }

    public UserAccount resetPasswordHash(String newPasswordHash) {
        return copy(requireText(newPasswordHash, "newPasswordHash"), status,
                true, nextSecurityVersion());
    }

    public UserAccount deactivate() {
        if (status == AccountStatus.INACTIVE) {
            return this;
        }
        return copy(passwordHash, AccountStatus.INACTIVE, mustChangePassword,
                nextSecurityVersion());
    }

    public UserAccount reactivate() {
        if (status == AccountStatus.ACTIVE) {
            return this;
        }
        return copy(passwordHash, AccountStatus.ACTIVE, mustChangePassword, securityVersion);
    }

    public UserAccount revokeAllSessions() {
        return copy(passwordHash, status, mustChangePassword, nextSecurityVersion());
    }

    private UserAccount copy(String nextPasswordHash, AccountStatus nextStatus,
            boolean nextMustChangePassword, long nextSecurityVersion) {
        return new UserAccount(id, username, normalizedUsername, displayName, nextPasswordHash,
                nextStatus, nextMustChangePassword, nextSecurityVersion, revision);
    }

    private long nextSecurityVersion() {
        try {
            return Math.incrementExact(securityVersion);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("securityVersion cannot be incremented", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UserAccount account && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "UserAccount[id=" + id + ", username=" + username + ", status=" + status
                + ", mustChangePassword=" + mustChangePassword + ", securityVersion="
                + securityVersion + ", revision=" + revision + "]";
    }
}
