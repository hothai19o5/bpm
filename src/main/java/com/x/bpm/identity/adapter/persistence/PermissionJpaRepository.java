package com.x.bpm.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PermissionJpaRepository extends JpaRepository<PermissionEntity, UUID> {
    List<PermissionEntity> findAllByOrderByCodeAsc();
    Optional<PermissionEntity> findByCode(String code);
}
