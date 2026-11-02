package com.x.bpm.task.application.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal task boundary. User/group IDs must come from trusted identity data,
 * never directly from client claims. RBAC, scope and assignment resolution are
 * applied by later application use cases, not by Flowable's identity tables.
 */
public interface TaskEnginePort {
    List<EngineTask> findActiveByInstance(String instanceId);

    List<EngineTask> findAssigned(String instanceId, String userId);

    /** Returns unclaimed tasks matching either an explicit candidate user or group. */
    List<EngineTask> findCandidates(String instanceId, String userId, Set<String> groupIds);

    EngineTask claim(String taskId, String userId, Set<String> groupIds);

    /** Requires the task to have been assigned/claimed by this user. */
    void complete(String taskId, String userId, Map<String, Object> variables);
}
