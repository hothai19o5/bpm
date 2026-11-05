package com.x.bpm.task.adapter.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskCompletionReceiptJpaRepository extends JpaRepository<TaskCompletionReceiptEntity,UUID> {
    Optional<TaskCompletionReceiptEntity> findByActorIdAndRequestKey(UUID actorId,UUID requestKey);
}
