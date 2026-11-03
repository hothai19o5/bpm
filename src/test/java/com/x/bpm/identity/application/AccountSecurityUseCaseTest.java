package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSecurityUseCaseTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void resetPasswordStoresTemporaryHashAndInvalidatesOldSecurityVersion() {
        var accounts = mock(AuthAccountPort.class);
        var grants = mock(AuthGrantPort.class);
        var hashes = mock(PasswordHashPort.class);
        var account = account(AccountStatus.ACTIVE, false, 4);
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(hashes.matches("temporary horse password", "stored-hash")).thenReturn(false);
        when(hashes.hash("temporary horse password")).thenReturn("temporary-hash");
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(grants.findUnrevokedGrantIdsByUserId(USER_ID)).thenReturn(List.of());
        var useCase = useCase(accounts, grants, hashes);

        useCase.resetPassword(USER_ID, "temporary horse password");

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("temporary-hash");
        assertThat(saved.getValue().mustChangePassword()).isTrue();
        assertThat(saved.getValue().securityVersion()).isEqualTo(5);
    }

    @Test
    void deactivationIncrementsVersionWhileReactivationDoesNotRestoreOldTokens() {
        var accounts = mock(AuthAccountPort.class);
        var grants = mock(AuthGrantPort.class);
        var hashes = mock(PasswordHashPort.class);
        when(accounts.findByIdForUpdate(USER_ID))
                .thenReturn(Optional.of(account(AccountStatus.ACTIVE, false, 4)))
                .thenReturn(Optional.of(account(AccountStatus.INACTIVE, false, 5)));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(grants.findUnrevokedGrantIdsByUserId(USER_ID)).thenReturn(List.of());
        var useCase = useCase(accounts, grants, hashes);

        useCase.deactivate(USER_ID);
        useCase.reactivate(USER_ID);

        var saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(saved.getAllValues().get(0).securityVersion()).isEqualTo(5);
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.getAllValues().get(1).securityVersion()).isEqualTo(5);
    }

    private AccountSecurityUseCase useCase(AuthAccountPort accounts, AuthGrantPort grants,
            PasswordHashPort hashes) {
        return new AccountSecurityUseCase(accounts, grants, hashes, new PasswordPolicy(),
                new ImmediateTransactions(),
                Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC));
    }

    private UserAccount account(AccountStatus status, boolean mustChangePassword,
            long securityVersion) {
        return new UserAccount(USER_ID, "employee.one", "employee.one", "Employee One",
                "stored-hash", status, mustChangePassword, securityVersion, 0);
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
