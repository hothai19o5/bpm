package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.RefreshCredential;
import com.x.bpm.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GRANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void revokesOnlyGrantResolvedFromRefreshToken() {
        var accounts = mock(AuthAccountPort.class);
        var grants = mock(AuthGrantPort.class);
        var refreshTokens = mock(RefreshTokenIssuer.class);
        var tokenHash = "a".repeat(64);
        var account = new UserAccount(USER_ID, "employee.one", "employee.one", "Employee One",
                "stored-hash", AccountStatus.ACTIVE, false, 7, 0);
        var grant = new LoginGrant(GRANT_ID, USER_ID, 7, NOW.minusSeconds(3600),
                NOW.plusSeconds(7200), null, null, 0);
        var credential = new RefreshCredential(CREDENTIAL_ID, GRANT_ID, tokenHash,
                NOW.minusSeconds(3600), NOW.plusSeconds(7200), null, 0);
        when(refreshTokens.hash("raw-refresh")).thenReturn(tokenHash);
        when(grants.findCredentialByTokenHash(tokenHash)).thenReturn(Optional.of(credential));
        when(grants.findGrantById(GRANT_ID)).thenReturn(Optional.of(grant));
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(grants.findGrantByIdForUpdate(GRANT_ID)).thenReturn(Optional.of(grant));
        when(grants.findCredentialByIdForUpdate(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
        when(grants.saveGrant(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var useCase = new LogoutUseCase(accounts, grants, refreshTokens,
                new ImmediateTransactions(), Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.logout(new RefreshTokenCommand("raw-refresh"));

        var revoked = ArgumentCaptor.forClass(LoginGrant.class);
        verify(grants).saveGrant(revoked.capture());
        assertThat(revoked.getValue().revokedAt()).isEqualTo(NOW);
        assertThat(revoked.getValue().revokeReason()).isEqualTo("logout");
    }

    @Test
    void unknownTokenIsAnIdempotentNoOp() {
        var accounts = mock(AuthAccountPort.class);
        var grants = mock(AuthGrantPort.class);
        var refreshTokens = mock(RefreshTokenIssuer.class);
        when(refreshTokens.hash("unknown-refresh")).thenReturn("b".repeat(64));
        when(grants.findCredentialByTokenHash("b".repeat(64))).thenReturn(Optional.empty());
        var useCase = new LogoutUseCase(accounts, grants, refreshTokens,
                new ImmediateTransactions(), Clock.fixed(NOW, ZoneOffset.UTC));

        useCase.logout(new RefreshTokenCommand("unknown-refresh"));

        verify(accounts, never()).findByIdForUpdate(any());
        verify(grants, never()).saveGrant(any());
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
