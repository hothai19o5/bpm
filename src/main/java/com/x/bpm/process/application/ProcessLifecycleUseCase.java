package com.x.bpm.process.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.*;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ProcessLifecycleUseCase {
    private final ProcessStore store;
    private final ProcessPublicationStore locks;
    private final ProcessLifecycleStore lifecycle;
    private final ProcessTransactionRunner transactions;
    private final CurrentActorProvider actors;
    private final AuditEventRecorder audit;
    private final ProcessValidationPort validator;
    public ProcessLifecycleUseCase(ProcessStore store,ProcessPublicationStore locks,ProcessLifecycleStore lifecycle,
            ProcessTransactionRunner transactions,CurrentActorProvider actors,AuditEventRecorder audit,ProcessValidationPort validator) {
        this.store=store;this.locks=locks;this.lifecycle=lifecycle;this.transactions=transactions;
        this.actors=actors;this.audit=audit;this.validator=validator;
    }
    public ProcessArtifact mutate(UUID id,ProcessLifecycleCommand command,UUID key,ProcessLifecycleOperation operation) {
        var actor=actors.requireCurrentActor();var policy=new AuthorizationPolicy();
        boolean design=operation==ProcessLifecycleOperation.CLONE||operation==ProcessLifecycleOperation.DUPLICATE;
        policy.requireGranted(policy.requirePermission(actor,design?PermissionCatalog.PROCESS_DESIGN:PermissionCatalog.PROCESS_PUBLISH));
        if(command==null||command.versionId()==null||command.expectedRevision()==null||command.expectedRevision()<0||key==null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thiếu version, revision hoặc Idempotency-Key.");
        if(operation==ProcessLifecycleOperation.DUPLICATE) {
            if(command.key()==null||!command.key().matches("[a-z][a-z0-9_-]{0,99}")||command.name()==null||command.name().isBlank()
                    ||command.name().length()>200||(command.description()!=null&&command.description().length()>4000))
                throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Mã/tên/mô tả quy trình mới không hợp lệ.");
        } else if(command.key()!=null||command.name()!=null||command.description()!=null||command.ownerUserId()!=null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thao tác này chỉ nhận version và revision.");
        UUID actorId=UUID.fromString(actor.userId());String signature=fingerprint(id,operation,command);
        return transactions.required(()->{
            locks.lock(id);
            var replay=lifecycle.replay(actorId,key,signature);if(replay.isPresent())return replay.get();
            var source=store.version(id,command.versionId()).orElseThrow(()->new ApplicationException(ErrorCode.NOT_FOUND,"Không tìm thấy version."));
            RevisionGuard.requireMatch(command.expectedRevision(),source.version().metadata().revision());
            ProcessArtifact target;
            switch(operation) {
                case CLONE -> {
                    if(source.version().bpmnXml()!=null)validator.validateDraft(source.definition().key(),model(source));
                    target=lifecycle.cloneVersion(id,command.versionId(),actorId);
                }
                case DUPLICATE -> {
                    var copied=source.version().bpmnXml()==null?null:validator.copyToKey(source.definition().key(),command.key(),model(source));
                    target=store.create(new CreateProcess(command.key(),command.name().strip(),command.description(),command.ownerUserId()==null?actorId:command.ownerUserId()),actorId);
                    if(copied!=null)target=store.saveDraft(target.definition().id(),new SaveProcessDraft(target.version().metadata().id(),0L,copied));
                }
                case RETIRE -> {
                    requirePublished(source);target=lifecycle.retire(id,command.versionId());
                }
                case ACTIVATE -> {
                    requirePublished(source);target=lifecycle.activate(id,command.versionId());
                }
                default -> throw new IllegalStateException();
            }
            lifecycle.remember(actorId,key,signature,target);
            audit.record("PROCESS_"+operation.name(),"PROCESS_DEFINITION",id.toString(),Map.of("sourceVersionId",command.versionId().toString(),
                    "targetDefinitionId",target.definition().id().toString(),"targetVersionId",target.version().metadata().id().toString(),"sourceRevision",command.expectedRevision()));
            return target;
        });
    }
    private static ProcessModelInput model(ProcessArtifact a) {return new ProcessModelInput(a.version().bpmnXml(),a.version().stepConfig(),a.version().formBindings());}
    private static void requirePublished(ProcessArtifact a) {
        if(a.version().metadata().status()!=ProcessVersionSummary.Status.PUBLISHED)
            throw new ApplicationException(ErrorCode.REVISION_CONFLICT,"Thao tác cần phiên bản PUBLISHED.");
    }
    private static String fingerprint(UUID id,ProcessLifecycleOperation operation,ProcessLifecycleCommand c) {
        try {
            var hash=MessageDigest.getInstance("SHA-256");
            for(var value:Arrays.asList(id,operation,c.versionId(),c.expectedRevision(),c.key(),c.name(),c.description(),c.ownerUserId())) {
                byte[] bytes=value==null?new byte[0]:value.toString().getBytes(StandardCharsets.UTF_8);
                hash.update(ByteBuffer.allocate(4).putInt(value==null?-1:bytes.length).array());hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        }catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
