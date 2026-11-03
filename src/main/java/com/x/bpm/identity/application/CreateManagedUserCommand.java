package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import java.util.Set;
import java.util.UUID;

public record CreateManagedUserCommand(String username, String displayName,
        String temporaryPassword, AccountStatus status, UUID departmentId, UUID positionId,
        Set<UUID> roleIds) {
    public CreateManagedUserCommand {
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
    }

    @Override
    public String toString() {
        return "CreateManagedUserCommand[username=" + username + ", displayName=" + displayName
                + ", temporaryPassword=<redacted>, status=" + status + "]";
    }
}
