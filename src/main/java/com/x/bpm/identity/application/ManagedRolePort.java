package com.x.bpm.identity.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ManagedRolePort {
    List<ManagedRole> findAllRoles();
    Set<ManagedRole> findRolesByUserId(UUID userId);
    Set<ManagedRole> findRolesByIds(Set<UUID> roleIds);
    void replaceUserRoles(UUID userId, Set<UUID> roleIds);
}
