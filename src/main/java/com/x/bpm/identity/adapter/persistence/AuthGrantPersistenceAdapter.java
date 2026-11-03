package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.AuthGrantPort;
import com.x.bpm.identity.application.NewLoginGrant;
import com.x.bpm.identity.application.NewRefreshCredential;
import com.x.bpm.identity.domain.LoginGrant;
import com.x.bpm.identity.domain.RefreshCredential;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter cho login grant và refresh credential. */
@Component
public class AuthGrantPersistenceAdapter implements AuthGrantPort {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UserJpaRepository users;
    private final LoginGrantJpaRepository grants;
    private final RefreshCredentialJpaRepository credentials;
    private final Clock clock;

    public AuthGrantPersistenceAdapter(UserJpaRepository users, LoginGrantJpaRepository grants,
            RefreshCredentialJpaRepository credentials, Clock clock) {
        this.users = users;
        this.grants = grants;
        this.credentials = credentials;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoginGrant createGrant(NewLoginGrant grant) {
        var user = users.findById(grant.userId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user account"));
        if (user.getSecurityVersion() != grant.securityVersion()) {
            throw new IllegalArgumentException("Grant security version does not match account");
        }
        requireFuture(grant.expiresAt(), "Grant expiry");
        var entity = new LoginGrantEntity();
        entity.setUser(user);
        entity.setSecurityVersion(grant.securityVersion());
        entity.setExpiresAt(grant.expiresAt());
        return IdentityPersistenceMapper.toDomain(grants.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginGrant> findGrantById(UUID grantId) {
        return grants.findDetailedById(grantId).map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoginGrant> findGrantByIdForUpdate(UUID grantId) {
        return grants.findDetailedByIdForUpdate(grantId).map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LoginGrant saveGrant(LoginGrant grant) {
        var entity = grants.findDetailedById(grant.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown login grant"));
        requireRevision(entity.getRevision(), grant.revision(), "Login grant");
        if (!entity.getUser().getId().equals(grant.userId())
                || entity.getSecurityVersion() != grant.securityVersion()
                || !entity.getExpiresAt().equals(grant.expiresAt())) {
            throw new IllegalArgumentException("Login grant identity and absolute expiry are immutable");
        }
        entity.setRevokedAt(grant.revokedAt());
        entity.setRevokeReason(grant.revokeReason());
        return IdentityPersistenceMapper.toDomain(grants.saveAndFlush(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> findUnrevokedGrantIdsByUserId(UUID userId) {
        return List.copyOf(grants.findUnrevokedIdsByUserId(userId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RefreshCredential createCredential(NewRefreshCredential credential) {
        var grant = grants.findDetailedById(credential.grantId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown login grant"));
        requireFuture(credential.expiresAt(), "Refresh credential expiry");
        if (credential.expiresAt().isAfter(grant.getExpiresAt())) {
            throw new IllegalArgumentException("Refresh credential cannot outlive login grant");
        }
        var entity = new RefreshCredentialEntity();
        entity.setGrant(grant);
        entity.setTokenHash(credential.tokenHash());
        entity.setExpiresAt(credential.expiresAt());
        return IdentityPersistenceMapper.toDomain(credentials.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshCredential> findCredentialByTokenHash(String tokenHash) {
        requireTokenHash(tokenHash);
        return credentials.findDetailedByTokenHash(tokenHash)
                .map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RefreshCredential> findCredentialByIdForUpdate(UUID credentialId) {
        return credentials.findDetailedByIdForUpdate(credentialId)
                .map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RefreshCredential saveCredential(RefreshCredential credential) {
        var entity = credentials.findById(credential.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown refresh credential"));
        requireRevision(entity.getRevision(), credential.revision(), "Refresh credential");
        if (!entity.getGrant().getId().equals(credential.grantId())
                || !entity.getTokenHash().equals(credential.tokenHash())
                || !entity.getExpiresAt().equals(credential.expiresAt())) {
            throw new IllegalArgumentException("Refresh credential identity and expiry are immutable");
        }
        entity.setConsumedAt(credential.consumedAt());
        return IdentityPersistenceMapper.toDomain(credentials.saveAndFlush(entity));
    }

    private void requireFuture(Instant instant, String field) {
        if (!instant.isAfter(Instant.now(clock))) {
            throw new IllegalArgumentException(field + " must be in the future");
        }
    }

    private static void requireRevision(long actual, long expected, String resource) {
        if (actual != expected) {
            throw new OptimisticLockingFailureException(resource + " revision is stale");
        }
    }

    private static void requireTokenHash(String tokenHash) {
        if (tokenHash == null || !SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("tokenHash must be a lowercase SHA-256 hex value");
        }
    }
}
