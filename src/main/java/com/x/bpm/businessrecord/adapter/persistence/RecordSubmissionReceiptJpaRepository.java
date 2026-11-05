package com.x.bpm.businessrecord.adapter.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface RecordSubmissionReceiptJpaRepository extends JpaRepository<RecordSubmissionReceiptEntity,UUID> {
    Optional<RecordSubmissionReceiptEntity> findByActorIdAndRequestKey(UUID actorId,UUID requestKey);
}
