package com.x.bpm.task.assignment.application;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.AuthenticatedAccount;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.adapter.security.AuthenticatedAccountPrincipal;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.organization.application.NewDepartment;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import com.x.bpm.task.assignment.domain.AssignmentStrategy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@IsolatedPostgresTest
class AssignmentResolverIntegrationTest {
    @Autowired private AssignmentResolver resolver;
    @Autowired private AuthAccountPort accounts;
    @Autowired private PasswordHashPort hashes;
    @Autowired private OrganizationManagementUseCase organizations;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void departmentAndRequesterManagerResolveFromPersistedCurrentDirectory() {
        var passwordHash = hashes.hash("temporary horse password");
        var requester = accounts.create(new NewUserAccount("assignment-requester",
                "Assignment Requester", passwordHash, AccountStatus.ACTIVE, false));
        var manager = accounts.create(new NewUserAccount("assignment-manager",
                "Assignment Manager", passwordHash, AccountStatus.ACTIVE, false));
        var actor = new AuthenticatedAccount(manager.id(), manager.username(), manager.displayName(),
                Set.of("organization.manage"), false, UUID.randomUUID(), manager.securityVersion());
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccountPrincipal(actor), null, List.of()));
        SecurityContextHolder.setContext(securityContext);
        var department = organizations.createDepartment(new NewDepartment("ASSIGNMENT",
                "Assignment Department", null, null, OrganizationStatus.ACTIVE));
        organizations.assignMembership(requester.id(), department.id(), null);
        organizations.assignMembership(manager.id(), department.id(), null);
        department = organizations.updateDepartment(department.id(), department.revision(),
                department.code(), department.name(), null, manager.id(), department.status());

        var managerResolution = resolver.resolve(new AssignmentRequest(
                AssignmentStrategy.REQUESTER_MANAGER, requester.id(), department.id(), null,
                null, null, null));
        var queueResolution = resolver.resolve(new AssignmentRequest(
                AssignmentStrategy.DEPARTMENT, requester.id(), department.id(), null,
                department.id(), null, null));

        assertThat(managerResolution.assigneeUserId()).isEqualTo(manager.id());
        assertThat(queueResolution.candidateUserIds()).containsExactly(manager.id());
    }
}
