package com.x.bpm.identity.adapter.persistence;

import com.x.bpm.identity.application.ManagedPermission;
import com.x.bpm.identity.application.ManagedRoleDetails;
import com.x.bpm.identity.application.NewManagedRole;
import com.x.bpm.identity.application.RoleAdministrationPort;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoleAdministrationPersistenceAdapter implements RoleAdministrationPort {
    private final RoleJpaRepository roles;
    private final PermissionJpaRepository permissions;
    private final RolePermissionJpaRepository rolePermissions;
    private final EntityManager entityManager;

    public RoleAdministrationPersistenceAdapter(RoleJpaRepository roles,
            PermissionJpaRepository permissions, RolePermissionJpaRepository rolePermissions,
            EntityManager entityManager) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagedPermission> findAllPermissions() {
        return permissions.findAllByOrderByCodeAsc().stream().map(RoleAdministrationPersistenceAdapter::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<ManagedPermission> findPermissionsByIds(Set<UUID> ids) {
        var result = new LinkedHashSet<ManagedPermission>();
        permissions.findAllById(ids).stream().map(RoleAdministrationPersistenceAdapter::map)
                .forEach(result::add);
        return Set.copyOf(result);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ensurePermissionCatalog(Set<String> codes) {
        for (var code : codes.stream().sorted().toList()) {
            if (permissions.findByCode(code).isEmpty()) {
                var entity = new PermissionEntity();
                entity.setCode(code);
                entity.setName(code);
                entity.setDescription("System permission " + code);
                permissions.save(entity);
            }
        }
        permissions.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagedRoleDetails> findAllRoleDetails() {
        return roles.findAllByOrderByCodeAsc().stream().map(this::mapDetails).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ManagedRoleDetails> findRoleById(UUID id) {
        return roles.findById(id).map(this::mapDetails);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ManagedRoleDetails createRole(NewManagedRole source) {
        var entity = new RoleEntity();
        apply(entity, source);
        entity = roles.saveAndFlush(entity);
        replacePermissions(entity.getId(), source.permissionIds());
        return mapDetails(entity);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ManagedRoleDetails updateRole(UUID id, long expectedRevision, NewManagedRole source) {
        var entity = roles.findById(id).orElseThrow();
        if (entity.getRevision() != expectedRevision) {
            throw new OptimisticLockingFailureException("Role revision is stale");
        }
        apply(entity, source);
        entity = roles.saveAndFlush(entity);
        replacePermissions(id, source.permissionIds());
        return mapDetails(entity);
    }

    private void replacePermissions(UUID roleId, Set<UUID> permissionIds) {
        rolePermissions.deleteByRoleId(roleId);
        rolePermissions.flush();
        var role = entityManager.getReference(RoleEntity.class, roleId);
        for (var permissionId : permissionIds) {
            var link = new RolePermissionEntity();
            link.setRole(role);
            link.setPermission(entityManager.getReference(PermissionEntity.class, permissionId));
            rolePermissions.save(link);
        }
        rolePermissions.flush();
    }

    private static void apply(RoleEntity entity, NewManagedRole source) {
        entity.setCode(source.code().strip());
        entity.setName(source.name().strip());
        entity.setDescription(source.description() == null ? null : source.description().strip());
        entity.setStatus(source.active() ? RoleEntity.Status.ACTIVE : RoleEntity.Status.INACTIVE);
    }

    private ManagedRoleDetails mapDetails(RoleEntity entity) {
        var mapped = new LinkedHashSet<ManagedPermission>();
        rolePermissions.findDetailedByRoleId(entity.getId()).stream()
                .map(RolePermissionEntity::getPermission)
                .map(RoleAdministrationPersistenceAdapter::map).forEach(mapped::add);
        return new ManagedRoleDetails(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getStatus() == RoleEntity.Status.ACTIVE,
                entity.getRevision(), mapped);
    }

    private static ManagedPermission map(PermissionEntity entity) {
        return new ManagedPermission(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription());
    }
}
