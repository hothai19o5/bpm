package com.x.bpm.process.application;

import com.x.bpm.process.application.engine.EngineProcessDefinition;
import java.util.Optional;
import java.util.UUID;

public interface ProcessPublicationStore {
    void lock(UUID definitionId);
    Optional<ProcessArtifact> replay(UUID definitionId, PublishProcess command, UUID actorId, UUID requestKey);
    ProcessArtifact freeze(UUID definitionId, PublishProcess command, UUID actorId, UUID requestKey,
                           String deploymentXml, String checksum, EngineProcessDefinition engine);
}
