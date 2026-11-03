package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.PasswordPolicy;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.shared.error.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

class ChangePasswordUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GRANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void changesHashClearsTemporaryFlagIncrementsVersionAndRevokesEveryGrant() {
        var fixture = fixture();
        when(fixture.passwordHashes.matches("current-password", "stored-hash")).thenReturn(true);
        when(fixture.passwordHashes.matches("new correct horse password", "stored-hash"))
                .thenReturn(false);
        when(fixture.passwordHashes.hash("new correct horse password"))
                .thenReturn("new-stored-hash");
        when(fixture.accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(fixture.grants.findUnrevokedGrantIdsByUserId(USER_ID))
                .thenReturn(List.of(GRANT_ID));
        when(fixture.grants.findGrantByIdForUpdate(GRANT_ID))
                .thenReturn(Optional.of(grant()));
        when(fixture.grants.saveGrant(any())).thenAnswer(call -> call.getArgument(0));

        fixture.useCase.changePassword(new ChangePasswordCommand("current-password",
                "new correct horse password"));

        var savedAccount = ArgumentCaptor.forClass(UserAccount.class);
        verify(fixture.accounts).save(savedAccount.capture());
        assertThat(savedAccount.getValue().passwordHash()).isEqualTo("new-stored-hash");
        assertThat(savedAccount.getValue().mustChangePassword()).isFalse();
        assertThat(savedAccount.getValue().securityVersion()).isEqualTo(8);
        var revokedGrant = ArgumentCaptor.forClass(LoginGrant.class);
        verify(fixture.grants).saveGrant(revokedGrant.capture());
        assertThat(revokedGrant.getValue().revokedAt()).isEqualTo(NOW);
        assertThat(revokedGrant.getValue().revokeReason()).isEqualTo("password-changed");
    }

    @Test
    void rejectsWrongCurrentPasswordWithoutWritingSecretsOrAccountState() {
        var fixture = fixture();
        when(fixture.passwordHashes.matches("wrong-current", "stored-hash"))
                .thenReturn(false);

        assertThatThrownBy(() -> fixture.useCase.changePassword(
                new ChangePasswordCommand("wrong-current", "new correct horse password")))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Mật khẩu hiện tại không hợp lệ.");

        verify(fixture.accounts, never()).save(any());
        verify(fixture.grants, never()).saveGrant(any());
    }

    @Test
    void reportsPasswordPolicyFailureWithoutEchoingTheRejectedPassword() {
        var fixture = fixture();
        when(fixture.passwordHashes.matches("current-password", "stored-hash")).thenReturn(true);
        when(fixture.passwordHashes.matches("short", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> fixture.useCase.changePassword(
                new ChangePasswordCommand("current-password", "short")))
                .isInstanceOfSatisfying(ApplicationException.class, error -> {
                    assertThat(error.getMessage()).doesNotContain("short");
                    assertThat(error.details()).allSatisfy(issue -> {
                        assertThat(issue.field()).isEqualTo("newPassword");
                        assertThat(issue.message()).doesNotContain("short");
                    });
                });
    }

    private Fixture fixture() {
        var accounts = mock(AuthAccountPort.class);
        var grants = mock(AuthGrantPort.class);
        var passwordHashes = mock(PasswordHashPort.class);
        var account = account();
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        var current = new AuthenticatedAccount(USER_ID, account.username(), account.displayName(),
                Set.of(), true, GRANT_ID, account.securityVersion());
        var useCase = new ChangePasswordUseCase(() -> current, accounts, grants,
                passwordHashes, new PasswordPolicy(), new ImmediateTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(useCase, accounts, grants, passwordHashes);
    }

    private UserAccount account() {
        return new UserAccount(USER_ID, "employee.one", "employee.one", "Employee One",
                "stored-hash", AccountStatus.ACTIVE, true, 7, 0);
    }

    private LoginGrant grant() {
        return new LoginGrant(GRANT_ID, USER_ID, 7, NOW.minusSeconds(3600),
                NOW.plusSeconds(7200), null, null, 0);
    }

    private record Fixture(ChangePasswordUseCase useCase, AuthAccountPort accounts,
            AuthGrantPort grants, PasswordHashPort passwordHashes) {
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
