package com.x.bpm.process.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessDefinitionJpaRepository extends JpaRepository<ProcessDefinitionEntity, UUID> { }
