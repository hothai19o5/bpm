package com.x.bpm.task.assignment.application;

import com.x.bpm.task.assignment.domain.AssignmentStrategy;
import java.util.Objects;
import java.util.UUID;

public record AssignmentRequest(AssignmentStrategy strategy, UUID requesterId,
        UUID requesterDepartmentId, UUID specificUserId, UUID departmentId, UUID roleId,
        String requiredPermission) {
    public AssignmentRequest {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(requesterDepartmentId, "requesterDepartmentId");
        requiredPermission = requiredPermission == null || requiredPermission.isBlank()
                ? null : requiredPermission.strip();
    }
}
