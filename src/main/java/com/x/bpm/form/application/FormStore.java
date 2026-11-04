package com.x.bpm.form.application;

import java.util.Optional;
import java.util.UUID;

public interface FormStore {
    FormArtifact create(CreateForm command, UUID actorId);
    FormArtifact saveDraft(UUID definitionId, SaveFormDraft command);
    FormMutationResult mutateVersion(UUID definitionId, FormVersionCommand command,
            UUID actorId, UUID requestKey, FormVersionOperation operation);
    Optional<FormArtifact> findVersion(UUID definitionId, UUID versionId);
}
