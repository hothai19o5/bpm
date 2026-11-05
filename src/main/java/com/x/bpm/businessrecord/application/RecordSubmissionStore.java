package com.x.bpm.businessrecord.application;
import com.x.bpm.businessrecord.domain.BusinessRecord;
import java.time.Instant;
import java.util.*;
public interface RecordSubmissionStore {
    boolean replay(UUID actorId,UUID key,String fingerprint,UUID recordId);
    void prepare(UUID recordId,UUID departmentId,String title,Map<String,Object> data);
    BusinessRecord finish(UUID recordId,String instanceId,Instant submittedAt,Instant completedAt);
    void remember(UUID actorId,UUID key,String fingerprint,UUID recordId,String instanceId);
}
