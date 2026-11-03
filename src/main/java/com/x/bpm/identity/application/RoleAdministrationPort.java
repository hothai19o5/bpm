package com.x.bpm.identity.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleAdministrationPort {
    List<ManagedPermission> findAllPermissions();
    Set<ManagedPermission> findPermissionsByIds(Set<UUID> ids);
    void ensurePermissionCatalog(Set<String> codes);
    List<ManagedRoleDetails> findAllRoleDetails();
    Optional<ManagedRoleDetails> findRoleById(UUID id);
    ManagedRoleDetails createRole(NewManagedRole role);
    ManagedRoleDetails updateRole(UUID id, long expectedRevision, NewManagedRole role);
}
