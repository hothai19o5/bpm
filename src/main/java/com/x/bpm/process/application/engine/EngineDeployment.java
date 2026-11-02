package com.x.bpm.process.application.engine;

import java.util.List;

public record EngineDeployment(String deploymentId, List<EngineProcessDefinition> definitions) {
    public EngineDeployment {
        definitions = List.copyOf(definitions);
    }
}
