package com.x.bpm.authorization.domain;

import java.util.Objects;
import java.util.Set;

/** Quan hệ resource do server tạo; field không áp dụng để null/rỗng. */
public record ResourceAccessContext(String requesterId, String processOwnerId,
        String assigneeId, Set<String> candidateUserIds, Set<String> participantUserIds,
        boolean draft) {
    public ResourceAccessContext {
        candidateUserIds = Set.copyOf(Objects.requireNonNullElse(candidateUserIds, Set.of()));
        participantUserIds = Set.copyOf(Objects.requireNonNullElse(participantUserIds, Set.of()));
    }

    public static ResourceAccessContext empty() {
        return new ResourceAccessContext(null, null, null, Set.of(), Set.of(), false);
    }
}
