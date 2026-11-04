package com.x.bpm.process.adapter.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface ProcessLifecycleReceiptJpaRepository extends JpaRepository<ProcessLifecycleReceiptEntity,UUID> {
    Optional<ProcessLifecycleReceiptEntity> findByActorIdAndRequestKey(UUID actorId,UUID requestKey);
}
