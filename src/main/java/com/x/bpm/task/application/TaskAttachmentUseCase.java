package com.x.bpm.task.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.businessrecord.domain.AttachmentFilePolicy;
import com.x.bpm.form.domain.FormFieldAccess;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.shared.application.RevisionGuard;
import com.x.bpm.shared.error.*;
import java.security.MessageDigest;
import java.util.*;

public final class TaskAttachmentUseCase {
    private final TaskRuntimeStore tasks;private final TaskInboxUseCase access;private final AttachmentStore files;private final AttachmentStorage storage;
    private final RecordTransactionRunner transactions;private final CurrentActorProvider actors;private final AuditEventRecorder audit;
    public TaskAttachmentUseCase(TaskRuntimeStore tasks,TaskInboxUseCase access,AttachmentStore files,AttachmentStorage storage,
            RecordTransactionRunner transactions,CurrentActorProvider actors,AuditEventRecorder audit){this.tasks=tasks;this.access=access;this.files=files;this.storage=storage;this.transactions=transactions;this.actors=actors;this.audit=audit;}
    public UploadedAttachment upload(String taskId,String fieldId,Long expectedRevision,String name,byte[] bytes){
        var actor=actors.requireCurrentActor();if(!actor.hasPermission("task.complete"))throw forbidden();
        if(expectedRevision==null||expectedRevision<0||fieldId==null||fieldId.isBlank())throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thiếu field hoặc revision.");
        return transactions.required(()->{
            var found=tasks.find(taskId);tasks.lockRecord(found.recordId());var task=tasks.find(taskId);if(!access.assigned(actor,task))throw forbidden();
            RevisionGuard.requireMatch(expectedRevision,task.recordRevision());var form=tasks.form(task);
            if(form.policy().accessTo(fieldId)!=FormFieldAccess.EDITABLE||!(((Map<?,?>)form.form().schema().get("properties")).get(fieldId) instanceof Map<?,?> definition)||!"ATTACHMENT".equals(definition.get("x-field-type")))throw forbidden();
            if(files.count(task.recordId())>=10)throw new ApplicationException(ErrorCode.VALIDATION_FAILED,"Hồ sơ chỉ được có tối đa 10 tệp.");
            String type=new AttachmentFilePolicy().validate(name,bytes),key="records/"+task.recordId()+"/"+UUID.randomUUID();
            String bucket=storage.bucket();
            storage.put(key,bytes,type);var stored=files.link(task.recordId(),fieldId,UUID.fromString(actor.userId()),name,type,bytes.length,sha256(bytes),bucket,key);
            audit.record("FILE_UPLOADED","RECORD_ATTACHMENT",stored.id().toString(),Map.of("recordId",task.recordId().toString(),"fieldId",fieldId,"size",bytes.length,"sha256",stored.sha256()));
            return new UploadedAttachment(new AttachmentInfo(stored.id(),stored.name()),files.revision(task.recordId()));
        });
    }
    public DownloadAttachment download(String taskId,UUID attachmentId){
        var actor=actors.requireCurrentActor();if(!actor.hasPermission("task.complete"))throw forbidden();
        return transactions.required(()->{
            var task=tasks.find(taskId);if(!access.assigned(actor,task)&&!(task.engine().assignee()==null&&access.candidate(actor,task)))throw forbidden();
            var file=files.find(attachmentId);var form=tasks.form(task);
            if(!file.recordId().equals(task.recordId())||form.policy().accessTo(file.fieldId())==FormFieldAccess.HIDDEN
                    ||!(form.data().get(file.fieldId()) instanceof List<?> ids)||!ids.contains(attachmentId.toString()))throw forbidden();
            byte[] bytes=storage.read(file.bucket(),file.objectKey());if(bytes.length!=file.size()||!sha256(bytes).equals(file.sha256()))throw new ApplicationException(ErrorCode.INTERNAL_ERROR,"Nội dung tệp không khớp metadata.");
            return new DownloadAttachment(file.name(),file.contentType(),bytes);
        });
    }
    public record UploadedAttachment(AttachmentInfo attachment,long recordRevision) { }
    public record DownloadAttachment(String name,String contentType,byte[] bytes) { }
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static ApplicationException forbidden(){return new ApplicationException(ErrorCode.FORBIDDEN,"Bạn không có quyền truy cập tệp trong công việc này.");}
}
