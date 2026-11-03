package com.x.bpm.task.assignment.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AssignmentResolution(UUID assigneeUserId, Set<UUID> candidateUserIds,
        Failure failure) {
    public AssignmentResolution {
        candidateUserIds = Set.copyOf(Objects.requireNonNullElse(candidateUserIds, Set.of()));
        var success = assigneeUserId != null || !candidateUserIds.isEmpty();
        if (success == (failure != null)) {
            throw new IllegalArgumentException("Resolution must be success or failure");
        }
        if (assigneeUserId != null && !candidateUserIds.isEmpty()) {
            throw new IllegalArgumentException("Direct assignment cannot also have candidates");
        }
    }

    public static AssignmentResolution direct(UUID userId) {
        return new AssignmentResolution(Objects.requireNonNull(userId, "userId"), Set.of(), null);
    }

    public static AssignmentResolution candidates(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("candidate user IDs must not be empty");
        }
        return new AssignmentResolution(null, userIds, null);
    }

    public static AssignmentResolution failed(Failure failure) {
        return new AssignmentResolution(null, Set.of(), Objects.requireNonNull(failure, "failure"));
    }

    public enum Failure {
        INVALID_CONFIGURATION,
        USER_NOT_FOUND,
        USER_INACTIVE,
        DEPARTMENT_NOT_FOUND,
        DEPARTMENT_INACTIVE,
        ROLE_NOT_FOUND,
        ROLE_INACTIVE,
        MANAGER_MISSING,
        MANAGER_INACTIVE,
        MANAGER_NOT_MEMBER,
        EMPTY_CANDIDATE_GROUP,
        SELF_APPROVAL
    }
}
