package com.x.bpm.organization.application;

import com.x.bpm.organization.domain.Department;
import com.x.bpm.organization.domain.DepartmentMembership;
import com.x.bpm.organization.domain.OrganizationStatus;
import com.x.bpm.shared.error.ApplicationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationManagementUseCaseTest {
    private static final UUID ROOT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CHILD_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void rejectsCycleAtAnyDepth() {
        var port = mock(OrganizationPort.class);
        when(port.findDepartmentById(ROOT_ID)).thenReturn(Optional.of(department(ROOT_ID, null, null)));
        when(port.findDepartmentById(CHILD_ID)).thenReturn(Optional.of(department(CHILD_ID, ROOT_ID, null)));
        when(port.findAllDepartments()).thenReturn(List.of(
                department(ROOT_ID, null, null), department(CHILD_ID, ROOT_ID, null)));
        var useCase = useCase(port);

        assertThatThrownBy(() -> useCase.updateDepartment(ROOT_ID, 0, "ROOT", "Root",
                CHILD_ID, null, OrganizationStatus.ACTIVE))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("chu kỳ");
    }

    @Test
    void managerMustBeActiveMemberOfTheManagedDepartment() {
        var port = mock(OrganizationPort.class);
        when(port.findDepartmentById(ROOT_ID)).thenReturn(Optional.of(department(ROOT_ID, null, null)));
        when(port.findUserById(MANAGER_ID)).thenReturn(Optional.of(
                new OrganizationUserRef(MANAGER_ID, "manager", "Manager", true)));
        when(port.findMembershipByUserId(MANAGER_ID)).thenReturn(Optional.of(
                new DepartmentMembership(UUID.randomUUID(), MANAGER_ID, CHILD_ID, null, 0)));
        var useCase = useCase(port);

        assertThatThrownBy(() -> useCase.updateDepartment(ROOT_ID, 0, "ROOT", "Root",
                null, MANAGER_ID, OrganizationStatus.ACTIVE))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("Manager phải ACTIVE");
    }

    @Test
    void resolvesManagerAndReturnsStableFailureReasons() {
        var port = mock(OrganizationPort.class);
        var membership = new DepartmentMembership(UUID.randomUUID(), USER_ID, ROOT_ID, null, 0);
        when(port.findMembershipByUserId(USER_ID)).thenReturn(Optional.of(membership));
        when(port.findDepartmentById(ROOT_ID)).thenReturn(Optional.of(
                department(ROOT_ID, null, MANAGER_ID)));
        when(port.findUserById(MANAGER_ID)).thenReturn(Optional.of(
                new OrganizationUserRef(MANAGER_ID, "manager", "Manager", true)));
        var useCase = useCase(port);

        assertThat(useCase.resolveRequesterManager(USER_ID).managerUserId()).isEqualTo(MANAGER_ID);
        assertThat(useCase.resolveRequesterManager(UUID.randomUUID()).failure())
                .isEqualTo(ManagerResolution.Failure.NO_MEMBERSHIP);
    }

    @Test
    void movingDepartmentManagerRequiresReplacingManagerFirst() {
        var port = mock(OrganizationPort.class);
        when(port.findUserById(MANAGER_ID)).thenReturn(Optional.of(
                new OrganizationUserRef(MANAGER_ID, "manager", "Manager", true)));
        when(port.findDepartmentById(CHILD_ID)).thenReturn(Optional.of(department(CHILD_ID, null, null)));
        when(port.findMembershipByUserId(MANAGER_ID)).thenReturn(Optional.of(
                new DepartmentMembership(UUID.randomUUID(), MANAGER_ID, ROOT_ID, null, 0)));
        when(port.findAllDepartments()).thenReturn(List.of(department(ROOT_ID, null, MANAGER_ID)));
        when(port.assignMembership(any(), any(), any())).thenThrow(new AssertionError("must not write"));
        var useCase = useCase(port);

        assertThatThrownBy(() -> useCase.assignMembership(MANAGER_ID, CHILD_ID, null))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("thay manager");
    }

    private OrganizationManagementUseCase useCase(OrganizationPort port) {
        return new OrganizationManagementUseCase(port, new ImmediateTransactions());
    }

    private Department department(UUID id, UUID parentId, UUID managerId) {
        return new Department(id, "D-" + id.toString().substring(0, 4), "Department",
                parentId, managerId, OrganizationStatus.ACTIVE, 0);
    }

    private static final class ImmediateTransactions implements OrganizationTransactionRunner {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }
}
