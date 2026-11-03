package com.x.bpm.audit.adapter.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
}
