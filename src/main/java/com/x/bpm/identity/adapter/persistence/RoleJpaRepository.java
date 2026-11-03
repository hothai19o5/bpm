package com.x.bpm.identity.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleJpaRepository extends JpaRepository<RoleEntity, UUID> {
    List<RoleEntity> findAllByOrderByCodeAsc();
}
