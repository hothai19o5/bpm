package com.x.bpm.task.assignment.application;

import java.util.Objects;
import java.util.UUID;

public record AssignmentRole(UUID id, boolean active) {
    public AssignmentRole {
        Objects.requireNonNull(id, "id");
    }
}
