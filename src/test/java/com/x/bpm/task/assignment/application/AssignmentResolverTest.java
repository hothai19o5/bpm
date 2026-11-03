package com.x.bpm.task.assignment.application;

import com.x.bpm.task.assignment.domain.AssignmentStrategy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignmentResolverTest {
    private static final UUID REQUESTER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTMENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID ROLE = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Test
    void specificUserRequiresActiveEligibleNonRequester() {
        var directory = mock(AssignmentDirectoryPort.class);
        when(directory.findUser(USER)).thenReturn(Optional.of(user(USER, true, "approve")));
        var resolver = new AssignmentResolver(directory);

        assertThat(resolver.resolve(request(AssignmentStrategy.SPECIFIC_USER, USER, null, null,
                "approve")).assigneeUserId()).isEqualTo(USER);
        assertThat(resolver.resolve(request(AssignmentStrategy.SPECIFIC_USER, REQUESTER, null,
                null, null)).failure()).isEqualTo(AssignmentResolution.Failure.USER_NOT_FOUND);

        when(directory.findUser(REQUESTER)).thenReturn(Optional.of(user(REQUESTER, true)));
        assertThat(resolver.resolve(request(AssignmentStrategy.SPECIFIC_USER, REQUESTER, null,
                null, null)).failure()).isEqualTo(AssignmentResolution.Failure.SELF_APPROVAL);
    }

    @Test
    void departmentQueueFiltersRequesterInactiveAndMissingPermission() {
        var directory = mock(AssignmentDirectoryPort.class);
        when(directory.findDepartment(DEPARTMENT)).thenReturn(Optional.of(
                new AssignmentDepartment(DEPARTMENT, true, null)));
        when(directory.findDepartmentMembers(DEPARTMENT)).thenReturn(List.of(
                user(REQUESTER, true, "approve"), user(USER, false, "approve"),
                user(OTHER, true, "approve"), user(UUID.randomUUID(), true, "other")));
        var resolution = new AssignmentResolver(directory).resolve(request(
                AssignmentStrategy.DEPARTMENT, null, DEPARTMENT, null, "approve"));

        assertThat(resolution.candidateUserIds()).containsExactly(OTHER);
    }

    @Test
    void roleQueueIsDepartmentScopedAndRequiresActiveRole() {
        var directory = mock(AssignmentDirectoryPort.class);
        when(directory.findDepartment(DEPARTMENT)).thenReturn(Optional.of(
                new AssignmentDepartment(DEPARTMENT, true, null)));
        when(directory.findRole(ROLE)).thenReturn(Optional.of(new AssignmentRole(ROLE, true)));
        when(directory.findDepartmentRoleMembers(DEPARTMENT, ROLE))
                .thenReturn(List.of(user(USER, true)));

        var resolution = new AssignmentResolver(directory).resolve(request(
                AssignmentStrategy.ROLE, null, DEPARTMENT, ROLE, null));

        assertThat(resolution.candidateUserIds()).containsExactly(USER);
        when(directory.findRole(ROLE)).thenReturn(Optional.of(new AssignmentRole(ROLE, false)));
        assertThat(new AssignmentResolver(directory).resolve(request(AssignmentStrategy.ROLE,
                null, DEPARTMENT, ROLE, null)).failure())
                .isEqualTo(AssignmentResolution.Failure.ROLE_INACTIVE);
    }

    @Test
    void requesterManagerUsesDepartmentSnapshotAndValidatesMembership() {
        var directory = mock(AssignmentDirectoryPort.class);
        when(directory.findDepartment(DEPARTMENT)).thenReturn(Optional.of(
                new AssignmentDepartment(DEPARTMENT, true, USER)));
        when(directory.findUser(USER)).thenReturn(Optional.of(user(USER, true)));
        var resolver = new AssignmentResolver(directory);

        assertThat(resolver.resolve(request(AssignmentStrategy.REQUESTER_MANAGER, null, null,
                null, null)).assigneeUserId()).isEqualTo(USER);

        when(directory.findUser(USER)).thenReturn(Optional.of(new AssignmentUser(USER, true,
                UUID.randomUUID(), Set.of(), Set.of())));
        assertThat(resolver.resolve(request(AssignmentStrategy.REQUESTER_MANAGER, null, null,
                null, null)).failure())
                .isEqualTo(AssignmentResolution.Failure.MANAGER_NOT_MEMBER);
    }

    @Test
    void emptyQueueAndInvalidConfigurationReturnStableFailures() {
        var directory = mock(AssignmentDirectoryPort.class);
        when(directory.findDepartment(DEPARTMENT)).thenReturn(Optional.of(
                new AssignmentDepartment(DEPARTMENT, true, null)));
        when(directory.findDepartmentMembers(DEPARTMENT)).thenReturn(List.of(user(REQUESTER, true)));
        var resolver = new AssignmentResolver(directory);

        assertThat(resolver.resolve(request(AssignmentStrategy.DEPARTMENT, null, DEPARTMENT,
                null, null)).failure())
                .isEqualTo(AssignmentResolution.Failure.EMPTY_CANDIDATE_GROUP);
        assertThat(resolver.resolve(request(AssignmentStrategy.SPECIFIC_USER, USER, DEPARTMENT,
                null, null)).failure())
                .isEqualTo(AssignmentResolution.Failure.INVALID_CONFIGURATION);
    }

    private AssignmentRequest request(AssignmentStrategy strategy, UUID specificUser,
            UUID department, UUID role, String permission) {
        return new AssignmentRequest(strategy, REQUESTER, DEPARTMENT, specificUser, department,
                role, permission);
    }

    private AssignmentUser user(UUID id, boolean active, String... permissions) {
        return new AssignmentUser(id, active, DEPARTMENT, Set.of(), Set.of(permissions));
    }
}
