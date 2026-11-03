package com.x.bpm.organization.adapter.persistence;

import com.x.bpm.identity.adapter.persistence.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DepartmentMemberJpaRepository extends JpaRepository<DepartmentMemberEntity, UUID> {
    @Query("""
            select m from DepartmentMemberEntity m
            join fetch m.user
            join fetch m.department
            left join fetch m.position
            where m.user.id = :userId
            """)
    Optional<DepartmentMemberEntity> findDetailedByUserId(@Param("userId") UUID userId);

    @Query("""
            select (count(m) > 0) from DepartmentMemberEntity m
            where m.department.id = :departmentId
              and m.user.status = :status
            """)
    boolean hasMembersWithStatus(@Param("departmentId") UUID departmentId,
            @Param("status") UserEntity.Status status);
}
