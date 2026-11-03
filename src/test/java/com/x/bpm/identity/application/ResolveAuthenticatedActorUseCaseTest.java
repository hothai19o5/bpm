package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.shared.error.ApplicationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResolveAuthenticatedActorUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID GRANT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private final AuthAccountPort accounts = mock(AuthAccountPort.class);
    private final AuthGrantPort grants = mock(AuthGrantPort.class);
    private final ResolveAuthenticatedActorUseCase useCase = new ResolveAuthenticatedActorUseCase(
            accounts, grants, new ImmediateTransactions(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void resolvesCurrentPermissionsInsteadOfJwtPermissionSnapshot() {
        when(accounts.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, 5)));
        when(grants.findGrantById(GRANT_ID)).thenReturn(Optional.of(
                grant(USER_ID, 5, NOW.plusSeconds(60), null)));
        when(accounts.findCurrentPermissions(USER_ID)).thenReturn(Set.of("CURRENT_PERMISSION"));

        var resolved = useCase.resolve(USER_ID.toString(), GRANT_ID.toString(), 5);

        assertThat(resolved.permissions()).containsExactly("CURRENT_PERMISSION");
        assertThat(resolved.actorContext().permissions()).containsExactly("CURRENT_PERMISSION");
    }

    @Test
    void rejectsInactiveRevokedExpiredWrongOwnerAndVersionMismatch() {
        var otherUser = UUID.fromString("30000000-0000-0000-0000-000000000002");
        when(accounts.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.INACTIVE, 5)));
        when(grants.findGrantById(GRANT_ID)).thenReturn(Optional.of(
                grant(USER_ID, 5, NOW.plusSeconds(60), null)));
        assertThatThrownBy(() -> useCase.resolve(
                USER_ID.toString(), GRANT_ID.toString(), 5))
                .isInstanceOf(ApplicationException.class);

        when(accounts.findById(USER_ID)).thenReturn(Optional.of(account(AccountStatus.ACTIVE, 5)));
        for (var invalidGrant : java.util.List.of(
                grant(otherUser, 5, NOW.plusSeconds(60), null),
                grant(USER_ID, 5, NOW.plusSeconds(60), NOW),
                grant(USER_ID, 5, NOW, null),
                grant(USER_ID, 4, NOW.plusSeconds(60), null))) {
            when(grants.findGrantById(GRANT_ID)).thenReturn(Optional.of(invalidGrant));
            assertThatThrownBy(() -> useCase.resolve(
                    USER_ID.toString(), GRANT_ID.toString(), 5))
                    .isInstanceOf(ApplicationException.class);
        }
    }

    @Test
    void rejectsMalformedIdentifiersBeforeReadingIdentity() {
        assertThatThrownBy(() -> useCase.resolve("not-a-uuid", GRANT_ID.toString(), 0))
                .isInstanceOf(ApplicationException.class);
    }

    private UserAccount account(AccountStatus status, long version) {
        return new UserAccount(USER_ID, "employee", "employee", "Employee", "hash",
                status, false, version, 0);
    }

    private LoginGrant grant(UUID userId, long version, Instant expiresAt, Instant revokedAt) {
        return new LoginGrant(GRANT_ID, userId, version, NOW.minusSeconds(60), expiresAt,
                revokedAt, revokedAt == null ? null : "revoked", 0);
    }

    private static final class ImmediateTransactions implements TransactionRunner {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }
}
