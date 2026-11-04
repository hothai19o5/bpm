package com.x.bpm.form.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FormVersionJpaRepository extends JpaRepository<FormVersionEntity, UUID> {
    Optional<FormVersionEntity> findByIdAndFormDefinitionId(UUID id, UUID formDefinitionId);
    @Query("select coalesce(max(v.versionNumber), 0) from FormVersionEntity v where v.formDefinition.id = :id")
    int maximumVersion(@Param("id") UUID definitionId);
}
