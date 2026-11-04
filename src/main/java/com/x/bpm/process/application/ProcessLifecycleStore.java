package com.x.bpm.process.application;

import java.util.Optional;
import java.util.UUID;

public interface ProcessLifecycleStore {
    Optional<ProcessArtifact> replay(UUID actorId, UUID requestKey, String fingerprint);
    void remember(UUID actorId, UUID requestKey, String fingerprint, ProcessArtifact target);
    ProcessArtifact cloneVersion(UUID id, UUID sourceId, UUID actorId);
    ProcessArtifact retire(UUID id, UUID versionId);
    ProcessArtifact activate(UUID id, UUID versionId);
}
