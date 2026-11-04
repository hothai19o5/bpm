package com.x.bpm.process.application;

import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface ProcessStore {
    ProcessArtifact saveDraft(UUID definitionId, SaveProcessDraft command);
    ProcessArtifact create(CreateProcess command, UUID actorId);
    Optional<ProcessDefinition> definition(UUID id);
    Optional<ProcessArtifact> version(UUID definitionId, UUID versionId);
    PageResult<ProcessVersionSummary> history(UUID definitionId, PageQuery page);
}
