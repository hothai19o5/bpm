package com.x.bpm.process.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessDefinitionJpaRepository extends JpaRepository<ProcessDefinitionEntity, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from ProcessDefinitionEntity d where d.id = :id")
    java.util.Optional<ProcessDefinitionEntity> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
}
