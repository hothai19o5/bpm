package com.x.bpm.form.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FormVersionJpaRepository extends JpaRepository<FormVersionEntity, UUID> {
    Optional<FormVersionEntity> findByIdAndFormDefinitionId(UUID id, UUID formDefinitionId);
}
