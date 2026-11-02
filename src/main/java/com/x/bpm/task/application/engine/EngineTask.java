package com.x.bpm.task.application.engine;

import java.time.Instant;
import java.util.Set;

public record EngineTask(String taskId, String instanceId, String definitionId,
                         String taskDefinitionKey, String name, String assignee,
                         Set<String> candidateUserIds, Set<String> candidateGroupIds,
                         Instant createdAt) {
    public EngineTask {
        candidateUserIds = Set.copyOf(candidateUserIds);
        candidateGroupIds = Set.copyOf(candidateGroupIds);
    }
}
