package com.x.bpm.identity.application;

import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.shared.error.ApplicationException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleAdministrationUseCaseTest {
    @Test
    void synchronizesOnlyTheSystemPermissionCatalog() {
        var port = mock(RoleAdministrationPort.class);
        var useCase = new RoleAdministrationUseCase(port, new ImmediateTransactions());

        useCase.synchronizePermissionCatalog();

        verify(port).ensurePermissionCatalog(PermissionCatalog.ALL);
    }

    @Test
    void rejectsPermissionOutsideTheCatalog() {
        var permissionId = UUID.randomUUID();
        var port = mock(RoleAdministrationPort.class);
        when(port.findPermissionsByIds(Set.of(permissionId))).thenReturn(Set.of(
                new ManagedPermission(permissionId, "unknown.permission", "Unknown", null)));
        var useCase = new RoleAdministrationUseCase(port, new ImmediateTransactions());

        assertThatThrownBy(() -> useCase.create(new NewManagedRole("custom", "Custom", null,
                true, Set.of(permissionId))))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("catalog");
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
