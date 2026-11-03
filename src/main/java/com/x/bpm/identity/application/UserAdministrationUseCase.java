package com.x.bpm.identity.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.application.OrganizationPort;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Quản trị account; không mở self-registration và không trả credential. */
public final class UserAdministrationUseCase {
    private final AuthAccountPort accounts;
    private final ManagedRolePort roles;
    private final OrganizationPort organizationPort;
    private final OrganizationManagementUseCase organizations;
    private final PasswordHashPort passwordHashes;
    private final PasswordPolicy passwordPolicy;
    private final AccountSecurityUseCase accountSecurity;
    private final TransactionRunner transactions;
    private final AuditEventRecorder audit;

    public UserAdministrationUseCase(AuthAccountPort accounts, ManagedRolePort roles,
            OrganizationPort organizationPort, OrganizationManagementUseCase organizations,
            PasswordHashPort passwordHashes, PasswordPolicy passwordPolicy,
            AccountSecurityUseCase accountSecurity, TransactionRunner transactions,
            AuditEventRecorder audit) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.organizationPort = Objects.requireNonNull(organizationPort, "organizationPort");
        this.organizations = Objects.requireNonNull(organizations, "organizations");
        this.passwordHashes = Objects.requireNonNull(passwordHashes, "passwordHashes");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "passwordPolicy");
        this.accountSecurity = Objects.requireNonNull(accountSecurity, "accountSecurity");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    public List<ManagedUserSummary> users() {
        return accounts.findAll().stream().map(this::summary).toList();
    }

    public ManagedUserSummary user(UUID userId) {
        return summary(requireAccount(userId));
    }

    public ManagedUserSummary create(CreateManagedUserCommand command) {
        validatePassword(command.temporaryPassword(), command.username());
        return transactions.required(() -> {
            validateRoles(command.roleIds());
            var account = accounts.create(new NewUserAccount(command.username(),
                    command.displayName(), passwordHashes.hash(command.temporaryPassword()),
                    command.status(), true));
            if (command.departmentId() != null) {
                organizations.assignMembership(account.id(), command.departmentId(),
                        command.positionId());
            }
            roles.replaceUserRoles(account.id(), command.roleIds());
            audit.record("USER_CREATED", "USER", account.id().toString(), auditDiff(
                    "username", account.username(),
                    "displayName", account.displayName(),
                    "status", account.status().name(),
                    "departmentId", stringValue(command.departmentId()),
                    "positionId", stringValue(command.positionId()),
                    "roleIds", sortedIds(command.roleIds())));
            return summary(account);
        });
    }

    public ManagedUserSummary update(UUID userId, long expectedRevision, String displayName,
            UUID departmentId, UUID positionId, Set<UUID> roleIds) {
        return transactions.required(() -> {
            var account = requireAccount(userId);
            RevisionGuard.requireMatch(expectedRevision, account.revision());
            validateRoles(roleIds);
            var saved = accounts.save(account.changeDisplayName(displayName));
            if (departmentId != null) {
                organizations.assignMembership(userId, departmentId, positionId);
            }
            roles.replaceUserRoles(userId, roleIds);
            audit.record("USER_UPDATED", "USER", userId.toString(), auditDiff(
                    "displayName", saved.displayName(),
                    "departmentId", stringValue(departmentId),
                    "positionId", stringValue(positionId),
                    "roleIds", sortedIds(roleIds),
                    "expectedRevision", expectedRevision));
            return summary(saved);
        });
    }

    public void deactivate(UUID userId) {
        transactions.required(() -> {
            if (organizationPort.isDepartmentManager(userId)) {
                throw new ApplicationException(ErrorCode.REVISION_CONFLICT,
                        "Phải thay manager trước khi khóa tài khoản.");
            }
            accountSecurity.deactivate(userId);
            audit.record("USER_DEACTIVATED", "USER", userId.toString(), Map.of());
            return null;
        });
    }

    public void reactivate(UUID userId) {
        transactions.required(() -> {
            accountSecurity.reactivate(userId);
            audit.record("USER_REACTIVATED", "USER", userId.toString(), Map.of());
            return null;
        });
    }

    public void resetPassword(UUID userId, String temporaryPassword) {
        var account = requireAccount(userId);
        validatePassword(temporaryPassword, account.username());
        transactions.required(() -> {
            accountSecurity.resetPassword(userId, temporaryPassword);
            audit.record("USER_PASSWORD_RESET", "USER", userId.toString(),
                    Map.of("mustChangePassword", true));
            return null;
        });
    }

    public List<ManagedRole> availableRoles() {
        return roles.findAllRoles();
    }

    private ManagedUserSummary summary(com.x.bpm.identity.domain.UserAccount account) {
        return new ManagedUserSummary(account.id(), account.username(), account.displayName(),
                account.status(), account.mustChangePassword(), account.revision(),
                organizationPort.findMembershipByUserId(account.id()).orElse(null),
                roles.findRolesByUserId(account.id()));
    }

    private com.x.bpm.identity.domain.UserAccount requireAccount(UUID userId) {
        return accounts.findById(userId).orElseThrow(() -> new ApplicationException(
                ErrorCode.NOT_FOUND, "Không tìm thấy tài khoản."));
    }

    private void validateRoles(Set<UUID> roleIds) {
        var requested = roleIds == null ? Set.<UUID>of() : Set.copyOf(roleIds);
        var found = roles.findRolesByIds(requested);
        if (found.size() != requested.size() || found.stream().anyMatch(role -> !role.active())) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Role không tồn tại hoặc không ACTIVE.");
        }
    }

    private void validatePassword(String password, String username) {
        if (!passwordPolicy.violations(password, username).isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Mật khẩu tạm không đáp ứng chính sách.");
        }
    }

    private static Map<String, Object> auditDiff(Object... values) {
        var diff = new LinkedHashMap<String, Object>();
        for (var index = 0; index < values.length; index += 2) {
            diff.put((String) values[index], values[index + 1]);
        }
        return diff;
    }

    private static String stringValue(UUID value) {
        return value == null ? null : value.toString();
    }

    private static List<String> sortedIds(Set<UUID> ids) {
        return (ids == null ? Set.<UUID>of() : ids).stream()
                .map(UUID::toString).sorted().toList();
    }
}
