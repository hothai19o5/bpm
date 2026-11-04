package com.x.bpm.form.adapter.persistence;

import com.x.bpm.form.application.CreateForm;
import com.x.bpm.form.application.FormArtifact;
import com.x.bpm.form.application.FormStore;
import com.x.bpm.form.application.SaveFormDraft;
import com.x.bpm.form.application.FormMutationResult;
import com.x.bpm.form.application.FormVersionCommand;
import com.x.bpm.form.application.FormVersionOperation;
import com.x.bpm.form.domain.FormVersionLifecycle;
import com.x.bpm.form.domain.JsonDocument;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.form.domain.FormDefinition;
import com.x.bpm.form.domain.FormVersion;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaFormStore implements FormStore {
    private final FormDefinitionJpaRepository definitions;
    private final FormVersionJpaRepository versions;
    private final EntityManager entities;
    private final FormVersionOperationJpaRepository operations;
    private final Clock clock;
    private final FormVersionLifecycle lifecycle = new FormVersionLifecycle();

    public JpaFormStore(FormDefinitionJpaRepository definitions,
            FormVersionJpaRepository versions, EntityManager entities,
            FormVersionOperationJpaRepository operations, Clock clock) {
        this.definitions = definitions;
        this.versions = versions;
        this.entities = entities;
        this.operations = operations;
        this.clock = clock;
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

    @Override
    @Transactional
    public FormArtifact saveDraft(UUID definitionId, SaveFormDraft command) {
        lockDefinition(definitionId);
        var version = versions.findByIdAndFormDefinitionId(command.versionId(), definitionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản biểu mẫu."));
        if (version.getStatus() != FormVersionEntity.Status.DRAFT) {
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT, "Chỉ được sửa phiên bản biểu mẫu nháp.");
        }
        RevisionGuard.requireMatch(command.expectedRevision(), version.getRevision());
        version.setSchemaJson(command.schema());
        version.setUiSchemaJson(command.uiSchema());
        return artifact(versions.saveAndFlush(version));
    }

    @Override
    @Transactional
    public FormMutationResult mutateVersion(UUID definitionId, FormVersionCommand command,
            UUID actorId, UUID requestKey, FormVersionOperation operation) {
        var definition = lockDefinition(definitionId);
        var previous = operations.findByActorIdAndRequestKey(actorId, requestKey);
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (receipt.getOperation() != operation || !receipt.getFormDefinition().getId().equals(definitionId)
                    || !receipt.getSourceVersion().getId().equals(command.versionId())
                    || receipt.getSourceRevision() != command.expectedRevision()) throw reusedKey();
            return new FormMutationResult(artifact(receipt.getTargetVersion()), true);
        }
        var source = versions.findByIdAndFormDefinitionId(command.versionId(), definitionId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản biểu mẫu."));
        lifecycle.validate(artifact(source).version(), command.expectedRevision());
        FormVersionEntity target;
        var actor = entities.getReference(UserEntity.class, actorId);
        if (operation == FormVersionOperation.PUBLISH) {
            lifecycle.requireDraft(artifact(source).version());
            source.setStatus(FormVersionEntity.Status.PUBLISHED);
            source.setPublishedBy(actor);
            source.setPublishedAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
            target = versions.saveAndFlush(source);
        } else {
            target = new FormVersionEntity();
            target.setFormDefinition(definition);
            target.setVersionNumber(lifecycle.nextVersion(versions.maximumVersion(definitionId)));
            target.setSchemaJson(JsonDocument.snapshot(source.getSchemaJson()));
            target.setUiSchemaJson(JsonDocument.snapshot(source.getUiSchemaJson()));
            target.setCreatedBy(actor);
            target = versions.saveAndFlush(target);
        }
        var receipt = new FormVersionOperationEntity();
        receipt.setActor(actor);
        receipt.setRequestKey(requestKey);
        receipt.setOperation(operation);
        receipt.setFormDefinition(definition);
        receipt.setSourceVersion(source);
        receipt.setSourceRevision(command.expectedRevision());
        receipt.setTargetVersion(target);
        try { operations.saveAndFlush(receipt); }
        catch (DataIntegrityViolationException ex) { throw reusedKey(); }
        return new FormMutationResult(artifact(target), false);
    }

    private FormDefinitionEntity lockDefinition(UUID definitionId) {
        return definitions.findByIdForUpdate(definitionId).orElseThrow(() ->
                new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy biểu mẫu."));
    }

    private ApplicationException reusedKey() {
        return new ApplicationException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key đã được dùng cho request khác.");
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
