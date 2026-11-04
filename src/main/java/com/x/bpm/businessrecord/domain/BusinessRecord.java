package com.x.bpm.businessrecord.domain;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.x.bpm.form.domain.JsonDocument;

public record BusinessRecord(UUID id,String businessKey,UUID processDefinitionId,UUID processVersionId,UUID formVersionId,
        UUID requesterId,String title,Status status,Map<String,Object> data,long revision,Instant updatedAt,String engineInstanceId) {
    public BusinessRecord {data=JsonDocument.snapshot(data);}
    public enum Status {DRAFT,IN_PROGRESS,CHANGES_REQUESTED,COMPLETED,REJECTED}
}
