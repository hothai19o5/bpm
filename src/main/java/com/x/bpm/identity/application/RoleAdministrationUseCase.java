package com.x.bpm.identity.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoleAdministrationUseCase {
    private final RoleAdministrationPort roles;
    private final TransactionRunner transactions;
    private final AuditEventRecorder audit;

    public RoleAdministrationUseCase(RoleAdministrationPort roles, TransactionRunner transactions,
            AuditEventRecorder audit) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    public List<ManagedPermission> permissions() {
        return roles.findAllPermissions();
    }

    public List<ManagedRoleDetails> roles() {
        return roles.findAllRoleDetails();
    }

    public ManagedRoleDetails create(NewManagedRole command) {
        validate(command);
        return transactions.required(() -> {
            var created = roles.createRole(command);
            audit.record("ROLE_CREATED", "ROLE", created.id().toString(), auditDiff(command, 0));
            return created;
        });
    }

    public ManagedRoleDetails update(UUID id, long expectedRevision, NewManagedRole command) {
        validate(command);
        return transactions.required(() -> {
            var updated = roles.updateRole(id, expectedRevision, command);
            audit.record("ROLE_UPDATED", "ROLE", id.toString(), auditDiff(command,
                    expectedRevision));
            return updated;
        });
    }

    public void synchronizePermissionCatalog() {
        transactions.required(() -> {
            roles.ensurePermissionCatalog(PermissionCatalog.ALL);
            return null;
        });
    }

    private void validate(NewManagedRole command) {
        if (command == null || command.code() == null || command.code().isBlank()
                || command.name() == null || command.name().isBlank()) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,
                    "Dữ liệu role không hợp lệ.");
        }
        var found = roles.findPermissionsByIds(command.permissionIds());
        if (found.size() != command.permissionIds().size()
                || found.stream().anyMatch(permission ->
                        !PermissionCatalog.ALL.contains(permission.code()))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Permission không thuộc catalog hoặc không tồn tại.");
        }
    }

    private static Map<String, Object> auditDiff(NewManagedRole command, long expectedRevision) {
        var diff = new LinkedHashMap<String, Object>();
        diff.put("code", command.code());
        diff.put("name", command.name());
        diff.put("description", command.description());
        diff.put("active", command.active());
        diff.put("permissionIds", command.permissionIds().stream()
                .map(UUID::toString).sorted().toList());
        diff.put("expectedRevision", expectedRevision);
        return diff;
    }
}
