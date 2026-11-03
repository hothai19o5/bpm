package com.x.bpm.identity.application;

import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.UserAccount;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID GRANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private final AuthAccountPort accounts = mock(AuthAccountPort.class);
    private final AuthGrantPort grants = mock(AuthGrantPort.class);
    private final PasswordHashPort passwordHashes = mock(PasswordHashPort.class);
    private final RefreshTokenIssuer refreshTokens = mock(RefreshTokenIssuer.class);
    private final AccessTokenIssuer accessTokens = mock(AccessTokenIssuer.class);
    private LoginUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LoginUseCase(accounts, grants, passwordHashes, refreshTokens,
                accessTokens, new ImmediateTransactions(),
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7), "dummy-hash");
    }

    @Test
    void createsGrantCredentialAndTokensFromLockedCurrentAccount() {
        var account = account(AccountStatus.ACTIVE, true);
        var expiresAt = NOW.plus(Duration.ofDays(7));
        var grant = new LoginGrant(GRANT_ID, USER_ID, 3, NOW, expiresAt,
                null, null, 0);
        var issuedAccess = new IssuedAccessToken("signed-jwt", "Bearer",
                NOW.plusSeconds(1800), 1800);
        when(accounts.findByUsername("Employee.One")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(java.util.Optional.of(account));
        when(passwordHashes.matches("correct password", "stored-hash")).thenReturn(true);
        when(grants.createGrant(any())).thenReturn(grant);
        when(refreshTokens.issue()).thenReturn(new IssuedRefreshToken("raw-refresh", "a".repeat(64)));
        when(accounts.findCurrentPermissions(USER_ID)).thenReturn(Set.of("PROCESS_READ"));
        when(accessTokens.issue(USER_ID, GRANT_ID, 3, Set.of("PROCESS_READ")))
                .thenReturn(issuedAccess);

        var result = useCase.login(new LoginCommand("Employee.One", "correct password"));

        assertThat(result.accessToken()).isSameAs(issuedAccess);
        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        assertThat(result.refreshExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.mustChangePassword()).isTrue();
        assertThat(result.user().permissions()).containsExactly("PROCESS_READ");
        assertThat(result.toString()).doesNotContain("signed-jwt").doesNotContain("raw-refresh");
        var credential = ArgumentCaptor.forClass(NewRefreshCredential.class);
        verify(grants).createCredential(credential.capture());
        assertThat(credential.getValue().grantId()).isEqualTo(GRANT_ID);
        assertThat(credential.getValue().tokenHash()).isEqualTo("a".repeat(64));
        assertThat(credential.getValue().expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void unknownUserStillPerformsDummyPasswordMatchAndReturnsSameSafeError() {
        when(accounts.findByUsername("missing")).thenReturn(java.util.Optional.empty());
        when(passwordHashes.matches("secret", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.login(new LoginCommand("missing", "secret")))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
                    assertThat(exception.getMessage()).doesNotContain("missing");
                });
        verify(passwordHashes).matches("secret", "dummy-hash");
        verify(grants, never()).createGrant(any());
    }

    @Test
    void rechecksPasswordAndStatusAfterLockBeforeCreatingGrant() {
        var initial = account(AccountStatus.ACTIVE, false);
        var locked = account(AccountStatus.INACTIVE, false);
        when(accounts.findByUsername("Employee.One")).thenReturn(java.util.Optional.of(initial));
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(java.util.Optional.of(locked));
        when(passwordHashes.matches("correct password", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> useCase.login(
                new LoginCommand("Employee.One", "correct password")))
                .isInstanceOf(ApplicationException.class);
        verify(grants, never()).createGrant(any());
    }

    @Test
    void rejectsPasswordThatChangedBetweenInitialReadAndLockedRead() {
        var account = account(AccountStatus.ACTIVE, false);
        when(accounts.findByUsername("Employee.One")).thenReturn(java.util.Optional.of(account));
        when(accounts.findByIdForUpdate(USER_ID)).thenReturn(java.util.Optional.of(account));
        when(passwordHashes.matches("correct password", "stored-hash"))
                .thenReturn(true, false);

        assertThatThrownBy(() -> useCase.login(
                new LoginCommand("Employee.One", "correct password")))
                .isInstanceOf(ApplicationException.class);
        verify(grants, never()).createGrant(any());
    }

    private UserAccount account(AccountStatus status, boolean mustChangePassword) {
        return new UserAccount(USER_ID, "Employee.One", "employee.one", "Employee One",
                "stored-hash", status, mustChangePassword, 3, 0);
    }

    private static final class ImmediateTransactions implements TransactionRunner {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    }
}
