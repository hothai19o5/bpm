package com.x.bpm.form.application;

import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import java.util.Optional;
import java.util.UUID;

public interface FormCatalogStore {
    PageResult<FormDefinition> list(FormCatalogQuery query);
    Optional<FormDefinition> definition(UUID id);
    PageResult<FormVersionSummary> history(UUID id, PageQuery page);
    PageResult<FormBindingOption> bindingOptions(FormCatalogQuery query);
    Optional<FormArtifact> bindingVersion(UUID versionId);
}
