package com.x.bpm.organization.application;

import java.util.UUID;

public record ManagerResolution(UUID managerUserId, Failure failure) {
    public ManagerResolution {
        if ((managerUserId == null) == (failure == null)) {
            throw new IllegalArgumentException("Exactly one manager result is required");
        }
    }

    public static ManagerResolution resolved(UUID managerUserId) {
        return new ManagerResolution(managerUserId, null);
    }

    public static ManagerResolution failed(Failure failure) {
        return new ManagerResolution(null, failure);
    }

    public enum Failure {
        NO_MEMBERSHIP,
        DEPARTMENT_INACTIVE,
        MANAGER_MISSING,
        MANAGER_INACTIVE
    }
}
