package com.x.bpm.process.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessPublicationJpaRepository extends JpaRepository<ProcessPublicationEntity, UUID> { }
