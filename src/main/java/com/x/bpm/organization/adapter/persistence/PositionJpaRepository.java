package com.x.bpm.organization.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PositionJpaRepository extends JpaRepository<PositionEntity, UUID> {
    boolean existsByCodeIgnoreCase(String code);
    List<PositionEntity> findAllByOrderByCodeAsc();
}
