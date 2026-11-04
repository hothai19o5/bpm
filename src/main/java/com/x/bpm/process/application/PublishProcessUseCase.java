package com.x.bpm.process.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.authorization.domain.PermissionCatalog;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PublishProcessUseCase {
    private final ProcessStore store;
    private final ProcessPublicationStore publications;
    private final ProcessTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final AuditEventRecorder audit;
    private final ProcessValidationPort validator;
    private final ProcessEnginePort engine;

    public PublishProcessUseCase(ProcessStore store, ProcessPublicationStore publications,
            ProcessTransactionRunner transactions, CurrentActorProvider actors, AuditEventRecorder audit,
            ProcessValidationPort validator, ProcessEnginePort engine) {
        this.store=store; this.publications=publications; this.transactions=transactions;
        this.actors=actors; this.audit=audit; this.validator=validator; this.engine=engine;
    }

    public ProcessArtifact publish(UUID id, PublishProcess command, UUID requestKey) {
        var actor=actors.requireCurrentActor();
        var policy=new AuthorizationPolicy();
        policy.requireGranted(policy.requirePermission(actor, PermissionCatalog.PROCESS_PUBLISH));
        if(command==null || command.versionId()==null || command.expectedRevision()==null
                || command.expectedRevision()<0 || requestKey==null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thiếu version, revision hoặc Idempotency-Key hợp lệ.");
        UUID actorId=UUID.fromString(actor.userId());
        return transactions.required(() -> {
            publications.lock(id);
            var replay=publications.replay(id,command,actorId,requestKey);
            if(replay.isPresent()) return replay.get();
            var artifact=store.version(id,command.versionId()).orElseThrow(() ->
                    new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy phiên bản quy trình."));
            var version=artifact.version();
            if(version.metadata().status()!=ProcessVersionSummary.Status.DRAFT)
                throw new ApplicationException(ErrorCode.REVISION_CONFLICT,"Chỉ được công bố bản nháp.");
            RevisionGuard.requireMatch(command.expectedRevision(),version.metadata().revision());
            String xml=validator.prepareDeployment(artifact.definition().key(),
                    new ProcessModelInput(version.bpmnXml(),version.stepConfig(),version.formBindings()));
            if(version.formVersionId()==null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Cần biểu mẫu chung cho các bước xử lý.",List.of(new ValidationIssue("formBindings","Cần ít nhất một bước xử lý gắn biểu mẫu.",null)));
            var entry=validator.submissionConfig(new ProcessModelInput(version.bpmnXml(),version.stepConfig(),version.formBindings())).orElseThrow();
            if(!version.formVersionId().equals(entry.formVersionId())||!entry.editable())
                throw new ApplicationException(ErrorCode.VALIDATION_FAILED,"Thiếu cấu hình khởi tạo hồ sơ.",
                        List.of(new ValidationIssue("fieldAccess","Gắn form chung và cho người tạo ít nhất một trường EDITABLE tại Start Event.",entry.elementId())));
            var deployment=engine.deploy(artifact.definition().key()+" v"+version.metadata().versionNumber(),
                    artifact.definition().key()+".bpmn20.xml",xml);
            if(deployment.definitions().size()!=1 || !deployment.definitions().getFirst().key().equals(artifact.definition().key())
                    || !deployment.definitions().getFirst().deploymentId().equals(deployment.deploymentId()))
                throw new ApplicationException(ErrorCode.INTERNAL_ERROR,"Engine trả mapping không hợp lệ.");
            var saved=publications.freeze(id,command,actorId,requestKey,xml,checksum(xml),deployment.definitions().getFirst(),true);
            audit.record("PROCESS_PUBLISHED","PROCESS_DEFINITION",id.toString(),
                    Map.of("versionId",command.versionId().toString(),"checksum",saved.version().artifactChecksum(),
                            "engineDefinitionId",saved.version().engineDefinitionId()));
            return saved;
        });
    }

    private static String checksum(String xml) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
