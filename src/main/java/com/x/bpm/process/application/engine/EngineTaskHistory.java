package com.x.bpm.process.application.engine;

import java.time.Instant;

public record EngineTaskHistory(String taskId, String taskDefinitionKey, String name,
                               String assignee, String completedBy, Instant startedAt,
                               Instant endedAt, String deleteReason) {
}
