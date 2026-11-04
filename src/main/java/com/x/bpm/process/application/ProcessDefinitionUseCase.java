package com.x.bpm.process.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.domain.ProcessDefinition;
import com.x.bpm.shared.application.PageQuery;
import com.x.bpm.shared.application.PageResult;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.shared.error.ErrorCode;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProcessDefinitionUseCase {
    private final ProcessStore store;
    private final ProcessTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final AuditEventRecorder audit;
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    public ProcessDefinitionUseCase(ProcessStore store, ProcessTransactionRunner transactions,
            CurrentActorProvider actors, AuditEventRecorder audit) {
        this.store = store;
        this.transactions = transactions;
        this.actors = actors;
        this.audit = audit;
    }

    public ProcessArtifact create(CreateProcess command) {
        var actor = actors.requireCurrentActor();
        policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.PROCESS_DESIGN));
        if (command == null || command.key() == null || !command.key().matches("[a-z][a-z0-9_-]{0,99}")
                || command.name() == null || command.name().isBlank() || command.name().length() > 200
                || (command.description() != null && command.description().length() > 4000))
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Key, tên hoặc mô tả quy trình không hợp lệ.");
        var actorId = UUID.fromString(actor.userId());
        var snapshot = new CreateProcess(command.key(), command.name().strip(), command.description(),
                command.ownerUserId() == null ? actorId : command.ownerUserId());
        return transactions.required(() -> {
            var artifact = store.create(snapshot, actorId);
            audit.record("PROCESS_CREATED", "PROCESS_DEFINITION", artifact.definition().id().toString(),
                    Map.of("key", snapshot.key(), "versionId", artifact.version().metadata().id().toString(),
                            "versionNumber", 1, "ownerUserId", snapshot.ownerUserId().toString()));
            return artifact;
        });
    }

    public ProcessDefinition definition(UUID id) {
        requireReader();
        return store.definition(id).orElseThrow(ProcessDefinitionUseCase::missing);
    }

    public ProcessArtifact version(UUID definitionId, UUID versionId) {
        requireReader();
        return store.version(definitionId, versionId).orElseThrow(ProcessDefinitionUseCase::missing);
    }

    public PageResult<ProcessVersionSummary> history(UUID definitionId, PageQuery page) {
        requireReader();
        if (!Set.of("versionNumber,asc", "versionNumber,desc").contains(page.sort()) || page.offset() > Integer.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST, "Phân trang lịch sử không hợp lệ.");
        store.definition(definitionId).orElseThrow(ProcessDefinitionUseCase::missing);
        return store.history(definitionId, page);
    }

    private void requireReader() {
        var actor = actors.requireCurrentActor();
        // C-06/C-09: quyền design/publish quản lý toàn bộ cấu hình. Owner/monitor không cấp quyền này.
        var permission = actor.hasPermission(PermissionCatalog.PROCESS_PUBLISH)
                ? PermissionCatalog.PROCESS_PUBLISH : PermissionCatalog.PROCESS_DESIGN;
        policy.requireGranted(policy.requirePermission(actor, permission));
    }

    private static ApplicationException missing() {
        return new ApplicationException(ErrorCode.NOT_FOUND, "Không tìm thấy quy trình hoặc phiên bản.");
    }
}
