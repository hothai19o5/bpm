package com.x.bpm.process.application.engine;

import java.time.Instant;
import java.util.List;

public record EngineProcessHistory(String instanceId, String definitionId, String businessKey,
                                  Instant startedAt, Instant endedAt, String endActivityId,
                                  String deleteReason, List<EngineTaskHistory> tasks) {
    public EngineProcessHistory {
        tasks = List.copyOf(tasks);
    }
}
