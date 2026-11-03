package com.x.bpm.identity.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityDomainTest {
    @Test
    void securitySensitiveAccountChangesIncrementVersionWithoutRestoringOldTokens() {
        var account = account();

        var passwordChanged = account.changePasswordHash("{bcrypt}new-hash");
        assertThat(passwordChanged.securityVersion()).isEqualTo(4);
        assertThat(passwordChanged.mustChangePassword()).isFalse();

        var inactive = passwordChanged.deactivate();
        assertThat(inactive.status()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(inactive.securityVersion()).isEqualTo(5);

        var activeAgain = inactive.reactivate();
        assertThat(activeAgain.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activeAgain.securityVersion()).isEqualTo(5);
        assertThat(activeAgain.revokeAllSessions().securityVersion()).isEqualTo(6);
    }

    @Test
    void grantRevocationIsIdempotentAndCredentialConsumptionIsNot() {
        var createdAt = Instant.parse("2026-11-03T00:00:00Z");
        var grant = new LoginGrant(UUID.randomUUID(), UUID.randomUUID(), 3, createdAt,
                createdAt.plus(7, ChronoUnit.DAYS), null, null, 0);
        var revoked = grant.revoke(createdAt.plusSeconds(1), "logout");

        assertThat(revoked.isUsableAt(createdAt.plusSeconds(2))).isFalse();
        assertThat(revoked.revoke(createdAt.plusSeconds(3), "duplicate")).isSameAs(revoked);

        var credential = new RefreshCredential(UUID.randomUUID(), grant.id(), "a".repeat(64),
                createdAt, createdAt.plus(1, ChronoUnit.DAYS), null, 0);
        var consumed = credential.consume(createdAt.plusSeconds(1));
        assertThat(consumed.isConsumed()).isTrue();
        assertThatThrownBy(() -> consumed.consume(createdAt.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private UserAccount account() {
        return new UserAccount(UUID.randomUUID(), "employee", "employee", "Employee",
                "{bcrypt}old-hash", AccountStatus.ACTIVE, true, 3, 0);
    }
}
