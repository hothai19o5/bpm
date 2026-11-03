package com.x.bpm.identity.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Trả danh tính an toàn từ principal đã đối chiếu trong security pipeline. */
public final class MeUseCase {
    private final CurrentAccountProvider currentAccounts;

    public MeUseCase(CurrentAccountProvider currentAccounts) {
        this.currentAccounts = Objects.requireNonNull(currentAccounts, "currentAccounts");
    }

    public Result get() {
        var current = currentAccounts.requireCurrentAccount();
        return new Result(current.userId(), current.username(), current.displayName(),
                current.permissions(), current.mustChangePassword());
    }

    public record Result(UUID id, String username, String displayName, Set<String> permissions,
            boolean mustChangePassword) {
        public Result {
            permissions = Set.copyOf(permissions);
        }
    }
}
