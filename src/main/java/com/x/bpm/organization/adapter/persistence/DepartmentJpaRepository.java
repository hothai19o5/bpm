package com.x.bpm.organization.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DepartmentJpaRepository extends JpaRepository<DepartmentEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);

    @Query("select d from DepartmentEntity d left join fetch d.parent left join fetch d.manager order by d.code")
    List<DepartmentEntity> findAllDetailed();

    @Query("select d from DepartmentEntity d left join fetch d.parent left join fetch d.manager where d.id = :id")
    Optional<DepartmentEntity> findDetailedById(@Param("id") UUID id);
}
