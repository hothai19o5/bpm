package com.x.bpm.form.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FormVersionOperationJpaRepository extends JpaRepository<FormVersionOperationEntity, UUID> {
    Optional<FormVersionOperationEntity> findByActorIdAndRequestKey(UUID actorId, UUID requestKey);
}
