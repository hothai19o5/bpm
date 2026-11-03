package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.AuthAccountPort;
import com.x.bpm.identity.application.NewUserAccount;
import com.x.bpm.identity.domain.AccountStatus;
import com.x.bpm.identity.domain.UserAccount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter cho tài khoản và quyền hiện tại. */
@Component
public class AuthAccountPersistenceAdapter implements AuthAccountPort {
    private final UserJpaRepository users;

    public AuthAccountPersistenceAdapter(UserJpaRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAccount> findAll() {
        return users.findAllByOrderByUsernameAsc().stream()
                .map(IdentityPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        return users.findByNormalizedUsername(UserAccount.normalizeUsername(username))
                .map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID userId) {
        return users.findById(userId).map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UserAccount> findByIdForUpdate(UUID userId) {
        return users.findByIdForUpdate(userId).map(IdentityPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findCurrentPermissions(UUID userId) {
        return Set.copyOf(new LinkedHashSet<>(
                users.findPermissionCodes(userId, RoleEntity.Status.ACTIVE)));
    }

    @Override
    @Transactional
    public UserAccount create(NewUserAccount account) {
        var entity = new UserEntity();
        entity.setUsername(account.username());
        entity.setDisplayName(account.displayName());
        entity.setPasswordHash(account.passwordHash());
        entity.setStatus(toEntityStatus(account.status()));
        entity.setMustChangePassword(account.mustChangePassword());
        entity.setSecurityVersion(0);
        return IdentityPersistenceMapper.toDomain(users.saveAndFlush(entity));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UserAccount save(UserAccount account) {
        var entity = users.findById(account.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user account"));
        if (entity.getRevision() != account.revision()) {
            throw new OptimisticLockingFailureException("User account revision is stale");
        }
        entity.setUsername(account.username());
        entity.setDisplayName(account.displayName());
        entity.setPasswordHash(account.passwordHash());
        entity.setStatus(toEntityStatus(account.status()));
        entity.setMustChangePassword(account.mustChangePassword());
        entity.setSecurityVersion(account.securityVersion());
        return IdentityPersistenceMapper.toDomain(users.saveAndFlush(entity));
    }

    private static UserEntity.Status toEntityStatus(AccountStatus status) {
        return UserEntity.Status.valueOf(status.name());
    }
}
