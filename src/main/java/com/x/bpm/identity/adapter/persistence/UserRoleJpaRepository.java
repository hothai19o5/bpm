package com.x.bpm.identity.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserRoleJpaRepository extends JpaRepository<UserRoleEntity, UUID> {
    @Query("select ur from UserRoleEntity ur join fetch ur.role where ur.user.id = :userId")
    List<UserRoleEntity> findDetailedByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from UserRoleEntity ur where ur.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
