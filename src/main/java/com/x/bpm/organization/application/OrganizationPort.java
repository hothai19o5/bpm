package com.x.bpm.organization.application;

import com.x.bpm.organization.domain.Department;
import com.x.bpm.organization.domain.DepartmentMembership;
import com.x.bpm.organization.domain.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationPort {
    List<Department> findAllDepartments();
    Optional<Department> findDepartmentById(UUID id);
    Department createDepartment(NewDepartment department);
    Department saveDepartment(Department department);

    List<Position> findAllPositions();
    Optional<Position> findPositionById(UUID id);
    Position createPosition(NewPosition position);
    Position savePosition(Position position);

    Optional<DepartmentMembership> findMembershipByUserId(UUID userId);
    DepartmentMembership assignMembership(UUID userId, UUID departmentId, UUID positionId);
    Optional<OrganizationUserRef> findUserById(UUID userId);
    boolean hasActiveMembers(UUID departmentId);
    boolean isDepartmentManager(UUID userId);
}
