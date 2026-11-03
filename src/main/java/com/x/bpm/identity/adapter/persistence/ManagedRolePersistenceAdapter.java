package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.ManagedRole;
import com.x.bpm.identity.application.ManagedRolePort;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ManagedRolePersistenceAdapter implements ManagedRolePort {
    private final RoleJpaRepository roles;
    private final UserRoleJpaRepository userRoles;
    private final EntityManager entityManager;

    public ManagedRolePersistenceAdapter(RoleJpaRepository roles,
            UserRoleJpaRepository userRoles, EntityManager entityManager) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagedRole> findAllRoles() {
        return roles.findAllByOrderByCodeAsc().stream().map(ManagedRolePersistenceAdapter::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<ManagedRole> findRolesByUserId(UUID userId) {
        var result = new LinkedHashSet<ManagedRole>();
        userRoles.findDetailedByUserId(userId).stream().map(UserRoleEntity::getRole)
                .map(ManagedRolePersistenceAdapter::map).forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<ManagedRole> findRolesByIds(Set<UUID> roleIds) {
        var result = new LinkedHashSet<ManagedRole>();
        roles.findAllById(roleIds).stream().map(ManagedRolePersistenceAdapter::map)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceUserRoles(UUID userId, Set<UUID> roleIds) {
        userRoles.deleteByUserId(userId);
        userRoles.flush();
        var user = entityManager.getReference(UserEntity.class, userId);
        for (var roleId : roleIds) {
            var entity = new UserRoleEntity();
            entity.setUser(user);
            entity.setRole(entityManager.getReference(RoleEntity.class, roleId));
            userRoles.save(entity);
        }
        userRoles.flush();
    }

    private static ManagedRole map(RoleEntity entity) {
        return new ManagedRole(entity.getId(), entity.getCode(), entity.getName(),
                entity.getStatus() == RoleEntity.Status.ACTIVE);
    }
}
