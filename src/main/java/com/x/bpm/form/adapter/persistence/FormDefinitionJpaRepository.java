package com.x.bpm.form.adapter.persistence;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FormDefinitionJpaRepository extends JpaRepository<FormDefinitionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FormDefinitionEntity f where f.id = :id")
    Optional<FormDefinitionEntity> findByIdForUpdate(@Param("id") UUID id);
}
