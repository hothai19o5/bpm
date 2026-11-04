package com.x.bpm.form.adapter.persistence;

import com.x.bpm.form.application.CreateForm;
import com.x.bpm.form.application.FormArtifact;
import com.x.bpm.form.application.FormStore;
import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaFormStore implements FormStore {
    private final FormDefinitionJpaRepository definitions;
    private final FormVersionJpaRepository versions;
    private final EntityManager entities;

    public JpaFormStore(FormDefinitionJpaRepository definitions,
            FormVersionJpaRepository versions, EntityManager entities) {
        this.definitions = definitions;
        this.versions = versions;
        this.entities = entities;
    }

    @Override
    @Transactional
    public FormArtifact create(CreateForm command, UUID actorId) {
        var actor = entities.getReference(UserEntity.class, actorId);
        var definition = new FormDefinitionEntity();
        definition.setKey(command.key());
        definition.setName(command.name());
        definition.setDescription(command.description());
        definition.setOwner(actor);
        try {
            definitions.saveAndFlush(definition);
        } catch (DataIntegrityViolationException ex) {
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Không thể tạo form với key này.");
        }
        var version = new FormVersionEntity();
        version.setFormDefinition(definition);
        version.setVersionNumber(1);
        version.setCreatedBy(actor);
        version.setSchemaJson(command.schema());
        version.setUiSchemaJson(command.uiSchema());
        return artifact(versions.saveAndFlush(version));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FormArtifact> findVersion(UUID definitionId, UUID versionId) {
        return versions.findByIdAndFormDefinitionId(versionId, definitionId).map(this::artifact);
    }

    private FormArtifact artifact(FormVersionEntity version) {
        var definition = version.getFormDefinition();
        return new FormArtifact(new FormDefinition(definition.getId(), definition.getKey(),
                definition.getName(), definition.getDescription(), definition.getOwner().getId(),
                FormDefinition.Status.valueOf(definition.getStatus().name()), definition.getRevision()),
                new FormVersion(version.getId(), definition.getId(), version.getVersionNumber(),
                        FormVersion.Status.valueOf(version.getStatus().name()), version.getSchemaJson(),
                        version.getUiSchemaJson(), version.getCreatedBy().getId(),
                        version.getPublishedBy() == null ? null : version.getPublishedBy().getId(),
                        version.getPublishedAt(), version.getRevision()));
    }
}
