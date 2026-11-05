package com.x.bpm.task.application;

import java.time.Instant;
import java.util.*;

public interface TaskCompletionStore {
    UUID locateRecord(String taskId);
    Optional<TaskCompletionResult> replay(UUID actorId,UUID key,String fingerprint);
    void prepare(BusinessTask task,CompleteTask command,UUID actorId,UUID key,String fingerprint,Map<String,Object> data);
    TaskCompletionResult finish(BusinessTask task,String action,String status,Instant completedAt);
    void remember(UUID actorId,UUID key,String fingerprint,TaskCompletionResult result);
    boolean hasCorrectionTask(UUID recordId,Set<String> activeActivityIds);
}
