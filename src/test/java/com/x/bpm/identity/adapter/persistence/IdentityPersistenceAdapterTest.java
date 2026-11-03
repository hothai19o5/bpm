package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.AuthGrantPort;
import com.x.bpm.identity.application.NewLoginGrant;
import com.x.bpm.identity.application.NewRefreshCredential;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.application.PasswordHashPort;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.support.IsolatedPostgresTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Persistence boundary tests use only the isolated PostgreSQL bpm_test database. */
@IsolatedPostgresTest
@Transactional
class IdentityPersistenceAdapterTest {
    @Autowired private AuthAccountPort accounts;
    @Autowired private AuthGrantPort grants;
    @Autowired private PasswordHashPort passwordHashes;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsAndFindsNormalizedAccountsWithoutExposingPasswordHashes() {
        var rawPassword = "correct horse battery staple";
        var encoded = passwordHashes.hash(rawPassword);
        var created = accounts.create(new NewUserAccount("  Employee.One  ", "Employee One",
                encoded, AccountStatus.ACTIVE, true));

        var found = accounts.findByUsername("EMPLOYEE.ONE").orElseThrow();
        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.username()).isEqualTo("Employee.One");
        assertThat(found.normalizedUsername()).isEqualTo("employee.one");
        assertThat(found.isActive()).isTrue();
        assertThat(found.mustChangePassword()).isTrue();
        assertThat(passwordHashes.matches(rawPassword, found.passwordHash())).isTrue();
        assertThat(found.toString()).doesNotContain(rawPassword).doesNotContain(encoded);
        assertThat(accounts.findByIdForUpdate(found.id())).contains(found);
    }

    @Test
    void readsOnlyPermissionsFromActiveRoles() {
        var account = createAccount("permission-user");
        var user = entityManager.find(UserEntity.class, account.id());
        assignPermission(user, "ACTIVE_ROLE", RoleEntity.Status.ACTIVE, "PROCESS_READ");
        assignPermission(user, "INACTIVE_ROLE", RoleEntity.Status.INACTIVE, "AUDIT_READ");
        entityManager.flush();

        assertThat(accounts.findCurrentPermissions(account.id())).containsExactly("PROCESS_READ");
    }

    @Test
    void persistsGrantAndRefreshHashWithinTheAbsoluteGrantExpiry() {
        var account = createAccount("grant-user");
        var grantExpiry = Instant.now().plus(7, ChronoUnit.DAYS);
        var grant = grants.createGrant(new NewLoginGrant(account.id(),
                account.securityVersion(), grantExpiry));
        var tokenHash = "a".repeat(64);
        var credential = grants.createCredential(new NewRefreshCredential(grant.id(), tokenHash,
                grantExpiry.minus(1, ChronoUnit.HOURS)));

        assertThat(grants.findGrantById(grant.id())).contains(grant);
        assertThat(grants.findGrantByIdForUpdate(grant.id())).contains(grant);
        assertThat(grants.findCredentialByTokenHash(tokenHash)).contains(credential);
        assertThat(grants.findCredentialByIdForUpdate(credential.id())).contains(credential);
        assertThat(credential.toString()).doesNotContain(tokenHash);
        assertThat(jdbc.queryForObject("select token_hash from refresh_credentials where id = ?",
                String.class, credential.id())).isEqualTo(tokenHash);
    }

    @Test
    void rejectsRefreshCredentialThatOutlivesItsGrant() {
        var account = createAccount("expiry-user");
        var grantExpiry = Instant.now().plus(1, ChronoUnit.DAYS);
        var grant = grants.createGrant(new NewLoginGrant(account.id(),
                account.securityVersion(), grantExpiry));

        assertThatThrownBy(() -> grants.createCredential(new NewRefreshCredential(grant.id(),
                "b".repeat(64), grantExpiry.plusSeconds(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot outlive");
    }

    @Test
    void persistsSecurityVersionRevocationAndConsumptionThroughLockedModels() {
        var account = createAccount("mutation-user");
        var lockedAccount = accounts.findByIdForUpdate(account.id()).orElseThrow();
        var changedAccount = accounts.save(lockedAccount.changePasswordHash(
                passwordHashes.hash("another correct horse battery staple")));
        assertThat(changedAccount.securityVersion()).isEqualTo(1);
        assertThat(changedAccount.revision()).isGreaterThan(account.revision());

        var grantExpiry = Instant.now().plus(1, ChronoUnit.DAYS);
        var grant = grants.createGrant(new NewLoginGrant(changedAccount.id(),
                changedAccount.securityVersion(), grantExpiry));
        var credential = grants.createCredential(new NewRefreshCredential(grant.id(),
                "c".repeat(64), grantExpiry.minusSeconds(1)));

        var revoked = grants.saveGrant(grants.findGrantByIdForUpdate(grant.id()).orElseThrow()
                .revoke(Instant.now(), "test-revoke"));
        var consumed = grants.saveCredential(grants.findCredentialByIdForUpdate(credential.id())
                .orElseThrow().consume(Instant.now()));

        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(revoked.revision()).isGreaterThan(grant.revision());
        assertThat(consumed.isConsumed()).isTrue();
        assertThat(consumed.revision()).isGreaterThan(credential.revision());
    }

    private com.x.bpm.identity.domain.UserAccount createAccount(String username) {
        return accounts.create(new NewUserAccount(username, "Test User",
                passwordHashes.hash("correct horse battery staple"), AccountStatus.ACTIVE, true));
    }

    private void assignPermission(UserEntity user, String roleCode, RoleEntity.Status status,
            String permissionCode) {
        var role = new RoleEntity();
        role.setCode(roleCode);
        role.setName(roleCode);
        role.setStatus(status);
        entityManager.persist(role);

        var permission = new PermissionEntity();
        permission.setCode(permissionCode);
        permission.setName(permissionCode);
        entityManager.persist(permission);

        var membership = new UserRoleEntity();
        membership.setUser(user);
        membership.setRole(role);
        entityManager.persist(membership);

        var rolePermission = new RolePermissionEntity();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        entityManager.persist(rolePermission);
    }
}
