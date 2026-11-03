package com.x.bpm.identity.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data chỉ tồn tại trong adapter, không lộ ra application. */
interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByNormalizedUsername(String normalizedUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :userId")
    Optional<UserEntity> findByIdForUpdate(@Param("userId") UUID userId);

    @Query("""
            select distinct rolePermission.permission.code
            from UserRoleEntity userRole, RolePermissionEntity rolePermission
            where userRole.user.id = :userId
              and userRole.role = rolePermission.role
              and userRole.role.status = :roleStatus
            """)
    Set<String> findPermissionCodes(@Param("userId") UUID userId,
            @Param("roleStatus") RoleEntity.Status roleStatus);
}
