package com.x.bpm.identity.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RolePermissionJpaRepository extends JpaRepository<RolePermissionEntity, UUID> {
    @Query("select rp from RolePermissionEntity rp join fetch rp.permission where rp.role.id = :roleId")
    List<RolePermissionEntity> findDetailedByRoleId(@Param("roleId") UUID roleId);

    @Modifying
    @Query("delete from RolePermissionEntity rp where rp.role.id = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);
}
