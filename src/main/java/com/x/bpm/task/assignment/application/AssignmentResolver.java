package com.x.bpm.task.assignment.application;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/** Resolve assignment từ directory server; không gọi Flowable hoặc nhận candidate từ client. */
public final class AssignmentResolver {
    private final AssignmentDirectoryPort directory;

    public AssignmentResolver(AssignmentDirectoryPort directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public AssignmentResolution resolve(AssignmentRequest request) {
        Objects.requireNonNull(request, "request");
        return switch (request.strategy()) {
            case SPECIFIC_USER -> resolveSpecificUser(request);
            case DEPARTMENT -> resolveDepartment(request);
            case ROLE -> resolveRole(request);
            case REQUESTER_MANAGER -> resolveRequesterManager(request);
        };
    }

    private AssignmentResolution resolveSpecificUser(AssignmentRequest request) {
        if (request.specificUserId() == null || request.departmentId() != null
                || request.roleId() != null) {
            return failed(AssignmentResolution.Failure.INVALID_CONFIGURATION);
        }
        return resolveDirect(request.specificUserId(), request.requesterId(),
                request.requiredPermission());
    }

    private AssignmentResolution resolveDepartment(AssignmentRequest request) {
        if (request.departmentId() == null || request.specificUserId() != null
                || request.roleId() != null) {
            return failed(AssignmentResolution.Failure.INVALID_CONFIGURATION);
        }
        var department = directory.findDepartment(request.departmentId()).orElse(null);
        if (department == null) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_NOT_FOUND);
        }
        if (!department.active()) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_INACTIVE);
        }
        return candidates(directory.findDepartmentMembers(department.id()), request);
    }

    private AssignmentResolution resolveRole(AssignmentRequest request) {
        if (request.departmentId() == null || request.roleId() == null
                || request.specificUserId() != null) {
            return failed(AssignmentResolution.Failure.INVALID_CONFIGURATION);
        }
        var department = directory.findDepartment(request.departmentId()).orElse(null);
        if (department == null) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_NOT_FOUND);
        }
        if (!department.active()) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_INACTIVE);
        }
        var role = directory.findRole(request.roleId()).orElse(null);
        if (role == null) {
            return failed(AssignmentResolution.Failure.ROLE_NOT_FOUND);
        }
        if (!role.active()) {
            return failed(AssignmentResolution.Failure.ROLE_INACTIVE);
        }
        return candidates(directory.findDepartmentRoleMembers(department.id(), role.id()), request);
    }

    private AssignmentResolution resolveRequesterManager(AssignmentRequest request) {
        if (request.specificUserId() != null || request.departmentId() != null
                || request.roleId() != null) {
            return failed(AssignmentResolution.Failure.INVALID_CONFIGURATION);
        }
        var department = directory.findDepartment(request.requesterDepartmentId()).orElse(null);
        if (department == null) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_NOT_FOUND);
        }
        if (!department.active()) {
            return failed(AssignmentResolution.Failure.DEPARTMENT_INACTIVE);
        }
        if (department.managerUserId() == null) {
            return failed(AssignmentResolution.Failure.MANAGER_MISSING);
        }
        var manager = directory.findUser(department.managerUserId()).orElse(null);
        if (manager == null || !manager.active()) {
            return failed(AssignmentResolution.Failure.MANAGER_INACTIVE);
        }
        if (!department.id().equals(manager.departmentId())) {
            return failed(AssignmentResolution.Failure.MANAGER_NOT_MEMBER);
        }
        if (!manager.hasPermission(request.requiredPermission())) {
            return failed(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP);
        }
        return directOrSelfApproval(manager.id(), request.requesterId());
    }

    private AssignmentResolution resolveDirect(UUID userId, UUID requesterId,
            String requiredPermission) {
        var user = directory.findUser(userId).orElse(null);
        if (user == null) {
            return failed(AssignmentResolution.Failure.USER_NOT_FOUND);
        }
        if (!user.active()) {
            return failed(AssignmentResolution.Failure.USER_INACTIVE);
        }
        if (!user.hasPermission(requiredPermission)) {
            return failed(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP);
        }
        return directOrSelfApproval(user.id(), requesterId);
    }

    private AssignmentResolution candidates(java.util.List<AssignmentUser> users,
            AssignmentRequest request) {
        var candidates = new LinkedHashSet<UUID>();
        users.stream().filter(AssignmentUser::active)
                .filter(user -> user.hasPermission(request.requiredPermission()))
                .map(AssignmentUser::id)
                .filter(id -> !id.equals(request.requesterId()))
                .sorted().forEach(candidates::add);
        return candidates.isEmpty() ? failed(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP)
                : AssignmentResolution.candidates(candidates);
    }

    private static AssignmentResolution directOrSelfApproval(UUID userId, UUID requesterId) {
        return userId.equals(requesterId)
                ? failed(AssignmentResolution.Failure.SELF_APPROVAL)
                : AssignmentResolution.direct(userId);
    }

    private static AssignmentResolution failed(AssignmentResolution.Failure failure) {
        return AssignmentResolution.failed(failure);
    }
}
