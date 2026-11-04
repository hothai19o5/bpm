package com.x.bpm.form.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.form.domain.FormSchemaContract;
import com.x.bpm.form.domain.JsonDocument;
import com.x.bpm.form.domain.FormVersionLifecycle;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

/** Quản trị form: create/save/validate và vòng đời publish/clone có audit transaction. */
public final class FormDefinitionUseCase {
    private final FormStore store;
    private final FormTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final AuthorizationPolicy policy;
    private final AuditEventRecorder audit;
    private final FormSchemaContract contract = new FormSchemaContract();

    public FormDefinitionUseCase(FormStore store, FormTransactionRunner transactions,
            CurrentActorProvider actors, AuthorizationPolicy policy, AuditEventRecorder audit) {
        this.store = store;
        this.transactions = transactions;
        this.actors = actors;
        this.policy = policy;
        this.audit = audit;
    }

    public FormArtifact create(CreateForm command) {
        var actor = actors.requireCurrentActor();
        policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.FORM_DESIGN));
        if (command == null || command.key() == null || !command.key().matches("[a-z][a-z0-9_-]{0,99}")
                || command.name() == null || command.name().isBlank() || command.name().length() > 200
                || (command.description() != null && command.description().length() > 4000)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Key, tên hoặc mô tả biểu mẫu không hợp lệ.");
        }
        contract.validate(command.schema(), command.uiSchema());
        var snapshot = new CreateForm(command.key(), command.name().strip(), command.description(),
                JsonDocument.snapshot(command.schema()), JsonDocument.snapshot(command.uiSchema()));
        return transactions.required(() -> {
            var result = store.create(snapshot, UUID.fromString(actor.userId()));
            audit.record("FORM_CREATED", "FORM_DEFINITION", result.definition().id().toString(),
                    Map.of("key", snapshot.key(), "versionId", result.version().id().toString(), "versionNumber", 1));
            return result;
        });
    }

    public FormArtifact version(UUID definitionId, UUID versionId) {
        var actor = actors.requireCurrentActor();
        requireReader(actor);
        // FORM_DESIGN là quyền quản trị toàn bộ form trong mô hình một Admin của C-06/C-09.
        return store.findVersion(definitionId, versionId).orElseThrow(() ->
                new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản biểu mẫu."));
    }

    public FormValidationResult validate(UUID definitionId, FormVersionCommand command) {
        var actor = actors.requireCurrentActor();
        requireReader(actor);
        requireCommand(command);
        var current = store.findVersion(definitionId, command.versionId()).orElseThrow(() ->
                new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy phiên bản biểu mẫu."));
        new FormVersionLifecycle().validate(current.version(), command.expectedRevision());
        return new FormValidationResult(current.version().id(), current.version().revision(), true);
    }

    public FormArtifact mutateVersion(UUID definitionId, FormVersionCommand command,
            UUID requestKey, FormVersionOperation operation) {
        var actor = actors.requireCurrentActor();
        if (operation == null || requestKey == null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Thiếu loại thao tác hoặc Idempotency-Key.");
        policy.requireGranted(policy.requirePermission(actor, operation == FormVersionOperation.PUBLISH
                ? PermissionCatalog.FORM_PUBLISH : PermissionCatalog.FORM_DESIGN));
        requireCommand(command);
        return transactions.required(() -> {
            var result = store.mutateVersion(definitionId, command, UUID.fromString(actor.userId()), requestKey, operation);
            if (!result.replayed()) audit.record(operation == FormVersionOperation.PUBLISH ? "FORM_PUBLISHED" : "FORM_DRAFT_CLONED",
                    "FORM_DEFINITION", definitionId.toString(), Map.of("sourceVersionId", command.versionId().toString(),
                            "versionId", result.artifact().version().id().toString(),
                            "versionNumber", result.artifact().version().versionNumber()));
            return result.artifact();
        });
    }

    private void requireReader(com.x.bpm.identity.domain.ActorContext actor) {
        if (!actor.hasPermission(PermissionCatalog.FORM_PUBLISH))
            policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.FORM_DESIGN));
    }

    private void requireCommand(FormVersionCommand command) {
        if (command == null || command.versionId() == null || command.expectedRevision() == null || command.expectedRevision() < 0)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Version và expectedRevision hợp lệ là bắt buộc.");
    }

    public FormArtifact saveDraft(UUID definitionId, SaveFormDraft command) {
        var actor = actors.requireCurrentActor();
        policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.FORM_DESIGN));
        if (command == null || command.versionId() == null || command.expectedRevision() == null
                || command.expectedRevision() < 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Version và expectedRevision hợp lệ là bắt buộc.");
        }
        contract.validate(command.schema(), command.uiSchema());
        var snapshot = new SaveFormDraft(command.versionId(), command.expectedRevision(),
                JsonDocument.snapshot(command.schema()), JsonDocument.snapshot(command.uiSchema()));
        return transactions.required(() -> {
            var saved = store.saveDraft(definitionId, snapshot);
            audit.record("FORM_DRAFT_SAVED", "FORM_DEFINITION", definitionId.toString(),
                    Map.of("versionId", saved.version().id().toString(),
                            "expectedRevision", snapshot.expectedRevision(), "revision", saved.version().revision()));
            return saved;
        });
    }
}
