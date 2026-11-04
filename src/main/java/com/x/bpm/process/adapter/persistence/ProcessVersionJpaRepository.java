package com.x.bpm.process.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessVersionJpaRepository extends JpaRepository<ProcessVersionEntity, UUID> {
    Optional<ProcessVersionEntity> findByIdAndProcessDefinitionId(UUID id, UUID definitionId);
}
