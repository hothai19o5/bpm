package com.x.bpm.organization.application;

import com.x.bpm.organization.domain.Department;
import com.x.bpm.organization.domain.DepartmentMembership;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.organization.domain.Position;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Use case quản trị cơ cấu; controller/JPA không chứa invariant cây và manager. */
public final class OrganizationManagementUseCase {
    private final OrganizationPort organizations;
    private final OrganizationTransactionRunner transactions;

    public OrganizationManagementUseCase(OrganizationPort organizations,
            OrganizationTransactionRunner transactions) {
        this.organizations = Objects.requireNonNull(organizations, "organizations");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public List<Department> departments() {
        return organizations.findAllDepartments();
    }

    public Department createDepartment(NewDepartment command) {
        return transactions.required(() -> {
            validateDepartment(null, command.parentId(), command.managerUserId(), command.status());
            return organizations.createDepartment(command);
        });
    }

    public Department updateDepartment(UUID id, long expectedRevision, String code, String name,
            UUID parentId, UUID managerUserId, OrganizationStatus status) {
        return transactions.required(() -> {
            var current = requireDepartment(id);
            RevisionGuard.requireMatch(expectedRevision, current.revision());
            validateDepartment(id, parentId, managerUserId, status);
            if (status == OrganizationStatus.INACTIVE
                    && (hasActiveChild(id) || organizations.hasActiveMembers(id))) {
                throw conflict("Không thể vô hiệu hóa phòng ban còn child hoặc member ACTIVE.");
            }
            return organizations.saveDepartment(new Department(id, code, name, parentId,
                    managerUserId, status, current.revision()));
        });
    }

    public List<Position> positions() {
        return organizations.findAllPositions();
    }

    public Position createPosition(NewPosition command) {
        return transactions.required(() -> organizations.createPosition(command));
    }

    public Position updatePosition(UUID id, long expectedRevision, String code, String name,
            String description, OrganizationStatus status) {
        return transactions.required(() -> {
            var current = organizations.findPositionById(id).orElseThrow(
                    OrganizationManagementUseCase::notFound);
            RevisionGuard.requireMatch(expectedRevision, current.revision());
            return organizations.savePosition(new Position(id, code, name, description, status,
                    current.revision()));
        });
    }

    public DepartmentMembership assignMembership(UUID userId, UUID departmentId,
            UUID positionId) {
        return transactions.required(() -> {
            var user = organizations.findUserById(userId).orElseThrow(
                    OrganizationManagementUseCase::notFound);
            if (!user.active() || requireDepartment(departmentId).status() != OrganizationStatus.ACTIVE) {
                throw conflict("User và phòng ban phải ACTIVE.");
            }
            if (positionId != null && organizations.findPositionById(positionId)
                    .filter(position -> position.status() == OrganizationStatus.ACTIVE).isEmpty()) {
                throw conflict("Chức danh không tồn tại hoặc không ACTIVE.");
            }
            var old = organizations.findMembershipByUserId(userId);
            if (old.isPresent() && !old.orElseThrow().departmentId().equals(departmentId)) {
                var managesOldDepartment = organizations.findAllDepartments().stream()
                        .anyMatch(department -> userId.equals(department.managerUserId()));
                if (managesOldDepartment) {
                    throw conflict("Phải thay manager trước khi chuyển phòng ban của user.");
                }
            }
            return organizations.assignMembership(userId, departmentId, positionId);
        });
    }

    public ManagerResolution resolveRequesterManager(UUID requesterId) {
        var membership = organizations.findMembershipByUserId(requesterId);
        if (membership.isEmpty()) {
            return ManagerResolution.failed(ManagerResolution.Failure.NO_MEMBERSHIP);
        }
        var department = organizations.findDepartmentById(membership.orElseThrow().departmentId())
                .orElse(null);
        if (department == null || department.status() != OrganizationStatus.ACTIVE) {
            return ManagerResolution.failed(ManagerResolution.Failure.DEPARTMENT_INACTIVE);
        }
        if (department.managerUserId() == null) {
            return ManagerResolution.failed(ManagerResolution.Failure.MANAGER_MISSING);
        }
        var manager = organizations.findUserById(department.managerUserId()).orElse(null);
        if (manager == null || !manager.active()) {
            return ManagerResolution.failed(ManagerResolution.Failure.MANAGER_INACTIVE);
        }
        return ManagerResolution.resolved(manager.id());
    }

    private void validateDepartment(UUID id, UUID parentId, UUID managerUserId,
            OrganizationStatus status) {
        Objects.requireNonNull(status, "status");
        if (parentId != null) {
            var parent = requireDepartment(parentId);
            if (parent.status() != OrganizationStatus.ACTIVE && status == OrganizationStatus.ACTIVE) {
                throw conflict("Parent của phòng ban ACTIVE phải ACTIVE.");
            }
            assertNoCycle(id, parentId);
        }
        if (managerUserId != null) {
            var manager = organizations.findUserById(managerUserId).orElseThrow(
                    OrganizationManagementUseCase::notFound);
            var membership = organizations.findMembershipByUserId(managerUserId);
            if (!manager.active() || id == null || membership.isEmpty()
                    || !membership.orElseThrow().departmentId().equals(id)) {
                throw conflict("Manager phải ACTIVE và thuộc chính phòng ban được quản lý.");
            }
        }
    }

    private void assertNoCycle(UUID id, UUID parentId) {
        if (id == null) {
            return;
        }
        var byId = new HashMap<UUID, Department>();
        organizations.findAllDepartments().forEach(department -> byId.put(department.id(), department));
        var cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(id)) {
                throw conflict("Quan hệ parent tạo chu kỳ phòng ban.");
            }
            var parent = byId.get(cursor);
            cursor = parent == null ? null : parent.parentId();
        }
    }

    private boolean hasActiveChild(UUID id) {
        return organizations.findAllDepartments().stream().anyMatch(department ->
                id.equals(department.parentId()) && department.status() == OrganizationStatus.ACTIVE);
    }

    private Department requireDepartment(UUID id) {
        return organizations.findDepartmentById(id).orElseThrow(
                OrganizationManagementUseCase::notFound);
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu tổ chức.");
    }

    private static ApplicationException conflict(String message) {
        return new ApplicationException(ErrorCode.REVISION_CONFLICT, message);
    }
}
