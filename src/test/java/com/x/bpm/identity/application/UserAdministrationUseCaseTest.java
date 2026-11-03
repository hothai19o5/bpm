package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.application.OrganizationPort;
import com.x.bpm.shared.error.ApplicationException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationUseCaseTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void createsAccountWithHashedTemporaryPasswordAndOnlyActiveRoles() {
        var fixture = fixture();
        when(fixture.passwords.hash("temporary horse password")).thenReturn("stored-hash");
        when(fixture.roles.findRolesByIds(Set.of(ROLE_ID))).thenReturn(Set.of(
                new ManagedRole(ROLE_ID, "employee", "Employee", true)));
        when(fixture.accounts.create(any())).thenReturn(account());
        when(fixture.roles.findRolesByUserId(USER_ID)).thenReturn(Set.of());

        fixture.useCase.create(new CreateManagedUserCommand("employee.one", "Employee One",
                "temporary horse password", AccountStatus.ACTIVE, null, null, Set.of(ROLE_ID)));

        var created = ArgumentCaptor.forClass(NewUserAccount.class);
        verify(fixture.accounts).create(created.capture());
        assertThat(created.getValue().passwordHash()).isEqualTo("stored-hash");
        assertThat(created.getValue().mustChangePassword()).isTrue();
        verify(fixture.roles).replaceUserRoles(USER_ID, Set.of(ROLE_ID));
    }

    @Test
    void refusesToDeactivateDepartmentManager() {
        var fixture = fixture();
        when(fixture.organizationPort.isDepartmentManager(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> fixture.useCase.deactivate(USER_ID))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("thay manager");

        verify(fixture.accountSecurity, never()).deactivate(any());
    }

    private Fixture fixture() {
        var accounts = mock(AuthAccountPort.class);
        var roles = mock(ManagedRolePort.class);
        var organizationPort = mock(OrganizationPort.class);
        var organizations = mock(OrganizationManagementUseCase.class);
        var passwords = mock(PasswordHashPort.class);
        var security = mock(AccountSecurityUseCase.class);
        var useCase = new UserAdministrationUseCase(accounts, roles, organizationPort,
                organizations, passwords, new PasswordPolicy(), security,
                new ImmediateTransactions());
        return new Fixture(useCase, accounts, roles, organizationPort, passwords, security);
    }

    private UserAccount account() {
        return new UserAccount(USER_ID, "employee.one", "employee.one", "Employee One",
                "stored-hash", AccountStatus.ACTIVE, true, 0, 0);
    }

    private record Fixture(UserAdministrationUseCase useCase, AuthAccountPort accounts,
            ManagedRolePort roles, OrganizationPort organizationPort, PasswordHashPort passwords,
            AccountSecurityUseCase accountSecurity) {
    }

    private static final class ImmediateTransactions implements TransactionRunner {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T requiresNew(Supplier<T> work) {
            return work.get();
        }
    }
}
