package com.x.bpm.process.application.engine;

import java.time.Instant;

public record EngineProcessInstance(String instanceId, String definitionId,
                                   String businessKey, Instant startedAt, boolean suspended) {
}
