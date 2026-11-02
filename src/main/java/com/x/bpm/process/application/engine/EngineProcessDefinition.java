package com.x.bpm.process.application.engine;

/** Engine IDs/versions are distinct from application catalog IDs/revisions. */
public record EngineProcessDefinition(String definitionId, String deploymentId,
                                      String key, String name, int version) {
}
