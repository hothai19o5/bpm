package com.x.bpm.form.application;

import java.util.Optional;
import java.util.UUID;

public interface FormStore {
    FormArtifact create(CreateForm command, UUID actorId);
    Optional<FormArtifact> findVersion(UUID definitionId, UUID versionId);
}
