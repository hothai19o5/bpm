package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.RefreshCredential;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GRANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID CREDENTIAL_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final String TOKEN_HASH = "a".repeat(64);

    private final AuthAccountPort accounts = mock(AuthAccountPort.class);
    private final AuthGrantPort grants = mock(AuthGrantPort.class);
    private final RefreshTokenIssuer refreshTokens = mock(RefreshTokenIssuer.class);
    private final AccessTokenIssuer accessTokens = mock(AccessTokenIssuer.class);
    private TrackingTransactions transactions;
    private RefreshTokenUseCase useCase;

    @BeforeEach
    void setUp() {
        transactions = new TrackingTransactions();
        useCase = new RefreshTokenUseCase(accounts, grants, refreshTokens, accessTokens,
                transactions, Clock.fixed(NOW, ZoneOffset.UTC));
        when(refreshTokens.hash("old-refresh")).thenReturn(TOKEN_HASH);
    }

    @Test
    void consumesCredentialAndIssuesOneReplacementWithinAbsoluteGrantExpiry() {
        var account = account(false);
        var grant = grant(null, null);
        var credential = credential(null);
        arrangeLockedState(account, grant, credential);
        var nextRefresh = new IssuedRefreshToken("next-refresh", "b".repeat(64));
        var nextAccess = new IssuedAccessToken("next-access", "Bearer",
                NOW.plusSeconds(1800), 1800);
        when(refreshTokens.issue()).thenReturn(nextRefresh);
        when(accounts.findCurrentPermissions(USER_ID)).thenReturn(Set.of("PROCESS_READ"));
        when(accessTokens.issue(USER_ID, GRANT_ID, 7, Set.of("PROCESS_READ")))
                .thenReturn(nextAccess);

        var result = useCase.refresh(new RefreshTokenCommand("old-refresh"));

        assertThat(result.accessToken()).isSameAs(nextAccess);
        assertThat(result.refreshToken()).isEqualTo("next-refresh");
        assertThat(result.refreshExpiresAt()).isEqualTo(grant.expiresAt());
        assertThat(result.toString()).doesNotContain("next-refresh").doesNotContain("next-access");
        var consumed = ArgumentCaptor.forClass(RefreshCredential.class);
        verify(grants).saveCredential(consumed.capture());
        assertThat(consumed.getValue().consumedAt()).isEqualTo(NOW);
        var replacement = ArgumentCaptor.forClass(NewRefreshCredential.class);
        verify(grants).createCredential(replacement.capture());
        assertThat(replacement.getValue().grantId()).isEqualTo(GRANT_ID);
        assertThat(replacement.getValue().tokenHash()).isEqualTo("b".repeat(64));
        assertThat(replacement.getValue().expiresAt()).isEqualTo(grant.expiresAt());
        assertThat(transactions.requiresNewCompleted).isTrue();
    }

    @Test
    void replayCommitsGrantRevocationBeforeReturningUnauthenticated() {
        var grant = grant(null, null);
        arrangeLockedState(account(false), grant, credential(NOW.minusSeconds(10)));
        when(grants.saveGrant(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> useCase.refresh(new RefreshTokenCommand("old-refresh")))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(transactions.requiresNewCompleted).isTrue();
                });

        var revoked = ArgumentCaptor.forClass(LoginGrant.class);
        verify(grants).saveGrant(revoked.capture());
        assertThat(revoked.getValue().revokedAt()).isEqualTo(NOW);
        assertThat(revoked.getValue().revokeReason()).isEqualTo("refresh-token-replay");
        verify(refreshTokens, never()).issue();
        verify(accessTokens, never()).issue(any(), any(), anyLong(), any());
    }

    @Test
    void temporaryPasswordRejectsRefreshWithoutConsumingCredential() {
        arrangeLockedState(account(true), grant(null, null), credential(null));

        assertThatThrownBy(() -> useCase.refresh(new RefreshTokenCommand("old-refresh")))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(transactions.requiresNewCompleted).isTrue();
        verify(grants, never()).saveCredential(any());
        verify(refreshTokens, never()).issue();
    }

    @Test
    void unknownTokenReturnsSameSafeErrorWithoutStartingMutationTransaction() {
        when(refreshTokens.hash("old-refresh")).thenReturn(TOKEN_HASH);
        when(grants.findCredentialByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.refresh(new RefreshTokenCommand("old-refresh")))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage()).doesNotContain("old-refresh");
                });
        assertThat(transactions.requiresNewStarted).isFalse();
    }

    private void arrangeLockedState(UserAccount account, LoginGrant grant,
            RefreshCredential credential) {
        when(grants.findCredentialByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(credential));
        when(grants.findGrantById(GRANT_ID)).thenReturn(Optional.of(grant));
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(account));
        when(grants.findGrantByIdForUpdate(GRANT_ID)).thenReturn(Optional.of(grant));
        when(grants.findCredentialByIdForUpdate(CREDENTIAL_ID))
                .thenReturn(Optional.of(credential));
    }

    private static UserAccount account(boolean mustChangePassword) {
        return new UserAccount(USER_ID, "employee.one", "employee.one", "Employee One",
                "stored-hash", AccountStatus.ACTIVE, mustChangePassword, 7, 0);
    }

    private static LoginGrant grant(Instant revokedAt, String reason) {
        return new LoginGrant(GRANT_ID, USER_ID, 7, NOW.minusSeconds(3600),
                NOW.plusSeconds(7200), revokedAt, reason, 0);
    }

    private static RefreshCredential credential(Instant consumedAt) {
        return new RefreshCredential(CREDENTIAL_ID, GRANT_ID, TOKEN_HASH,
                NOW.minusSeconds(3600), NOW.plusSeconds(7200), consumedAt, 0);
    }

    private static final class TrackingTransactions implements TransactionRunner {
        private boolean requiresNewStarted;
        private boolean requiresNewCompleted;

        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T requiresNew(Supplier<T> work) {
            requiresNewStarted = true;
            var result = work.get();
            requiresNewCompleted = true;
            return result;
        }
    }
}
