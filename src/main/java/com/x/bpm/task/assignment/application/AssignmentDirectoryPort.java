package com.x.bpm.task.assignment.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentDirectoryPort {
    Optional<AssignmentUser> findUser(UUID userId);
    Optional<AssignmentDepartment> findDepartment(UUID departmentId);
    Optional<AssignmentRole> findRole(UUID roleId);
    List<AssignmentUser> findDepartmentMembers(UUID departmentId);
    List<AssignmentUser> findDepartmentRoleMembers(UUID departmentId, UUID roleId);
}
