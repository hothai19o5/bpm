package com.x.bpm.identity.application;

import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class RoleAdministrationUseCase {
    private final RoleAdministrationPort roles;
    private final TransactionRunner transactions;

    public RoleAdministrationUseCase(RoleAdministrationPort roles, TransactionRunner transactions) {
        this.roles = Objects.requireNonNull(roles, "roles");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public List<ManagedPermission> permissions() {
        return roles.findAllPermissions();
    }

    public List<ManagedRoleDetails> roles() {
        return roles.findAllRoleDetails();
    }

    public ManagedRoleDetails create(NewManagedRole command) {
        validate(command);
        return transactions.required(() -> roles.createRole(command));
    }

    public ManagedRoleDetails update(UUID id, long expectedRevision, NewManagedRole command) {
        validate(command);
        return transactions.required(() -> roles.updateRole(id, expectedRevision, command));
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
}
