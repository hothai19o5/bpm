package com.x.bpm.task.assignment.application;

import java.util.Objects;
import java.util.UUID;

public record AssignmentDepartment(UUID id, boolean active, UUID managerUserId) {
    public AssignmentDepartment {
        Objects.requireNonNull(id, "id");
    }
}
