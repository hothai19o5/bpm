package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.organization.domain.DepartmentMembership;
import java.util.Set;
import java.util.UUID;

public record ManagedUserSummary(UUID id, String username, String displayName,
        AccountStatus status, boolean mustChangePassword, long revision,
        DepartmentMembership membership, Set<ManagedRole> roles) {
    public ManagedUserSummary {
        roles = Set.copyOf(roles);
    }
}
