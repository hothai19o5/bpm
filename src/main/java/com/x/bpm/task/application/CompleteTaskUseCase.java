package com.x.bpm.task.application;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.shared.application.*;
import com.x.bpm.shared.error.*;
import com.x.bpm.task.application.engine.TaskEnginePort;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class CompleteTaskUseCase {
    private final TaskRuntimeStore tasks;private final TaskCompletionStore completions;private final RecordTransactionRunner transactions;
    private final CurrentActorProvider actors;private final TaskInboxUseCase access;private final FormDataService forms;
    private final TaskEnginePort taskEngine;private final ProcessEnginePort processEngine;private final RecordEngineData encoding;private final AuditEventRecorder audit;
    public CompleteTaskUseCase(TaskRuntimeStore tasks,TaskCompletionStore completions,RecordTransactionRunner transactions,CurrentActorProvider actors,
            TaskInboxUseCase access,FormDataService forms,TaskEnginePort taskEngine,ProcessEnginePort processEngine,RecordEngineData encoding,AuditEventRecorder audit){
        this.tasks=tasks;this.completions=completions;this.transactions=transactions;this.actors=actors;this.access=access;this.forms=forms;
        this.taskEngine=taskEngine;this.processEngine=processEngine;this.encoding=encoding;this.audit=audit;
    }
    public TaskCompletionResult complete(String taskId,CompleteTask command,UUID requestKey){
        var actor=actors.requireCurrentActor();if(!actor.hasPermission("task.complete"))throw forbidden();
        if(taskId==null||taskId.isBlank()||taskId.length()>255||command==null||command.expectedRevision()==null||command.expectedRevision()<0||command.action()==null||command.patch()==null||requestKey==null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST,"Thiếu revision, action, patch hoặc Idempotency-Key.");
        UUID actorId=UUID.fromString(actor.userId());
        String fingerprint=RequestFingerprint.of(Map.of("taskId",taskId,"revision",command.expectedRevision(),"action",command.action().name(),"reason",Map.of("present",command.reason()!=null,"text",Objects.toString(command.reason(),"")),
                "values",command.patch().values(),"removeFields",command.patch().removeFields().stream().sorted().toList()));
        return transactions.required(()->{
            var replay=completions.replay(actorId,requestKey,fingerprint);if(replay.isPresent())return replay.get();
            // History locates the record even if another request has just completed this task.
            UUID recordId=completions.locateRecord(taskId);tasks.lockRecord(recordId);
            var previous=completions.replay(actorId,requestKey,fingerprint);if(previous.isPresent())return previous.get();
            BusinessTask task;
            try{task=tasks.find(taskId);}catch(ApplicationException missing){if(missing.code()==ErrorCode.NOT_FOUND)throw new ApplicationException(ErrorCode.TASK_CONFLICT,"Công việc đã được xử lý. Tải lại hồ sơ.");throw missing;}
            if(!access.assigned(actor,task))throw forbidden();
            RevisionGuard.requireMatch(command.expectedRevision(),task.recordRevision());
            var context=tasks.form(task);
            if(!context.actions().contains(command.action()))throw forbidden();
            if(command.action()==com.x.bpm.form.domain.FormAction.RESUBMIT&&!task.requesterCorrection())throw forbidden();
            var data=forms.applyPatch(context.form(),context.data(),command.patch(),context.policy(),command.action(),command.reason());
            var reset=command.action()==com.x.bpm.form.domain.FormAction.RESUBMIT?context.resetFieldsOnResubmit():Set.<String>of();
            if(!reset.isEmpty()){var refreshed=new LinkedHashMap<>(data);reset.forEach(refreshed::remove);data=refreshed;}
            RecordAttachmentGuard.requireLinked(context.form(),data);
            completions.prepare(task,command,actorId,requestKey,fingerprint,data,reset);
            taskEngine.complete(taskId,actor.userId(),Map.of("bpmFormData",encoding.encode(data),"bpmOutcome",command.action().name()));
            String status;Instant completedAt=null;
            if(processEngine.findRunningInstance(task.engine().instanceId()).isEmpty()){
                completedAt=processEngine.findHistory(task.engine().instanceId()).filter(history->history.endedAt()!=null)
                        .orElseThrow(()->new ApplicationException(ErrorCode.INTERNAL_ERROR,"Không đọc được kết quả engine.")).endedAt();
                status=command.action()==com.x.bpm.form.domain.FormAction.REJECT?"REJECTED":"COMPLETED";
            }else{
                var active=taskEngine.findActiveByInstance(task.engine().instanceId());
                if(active.isEmpty())throw new ApplicationException(ErrorCode.INTERNAL_ERROR,"Engine không có bước chờ hợp lệ.");
                status=completions.hasCorrectionTask(recordId,active.stream().map(t->t.taskDefinitionKey()).collect(Collectors.toSet()))?"CHANGES_REQUESTED":"IN_PROGRESS";
            }
            var result=completions.finish(task,command.action().name(),status,completedAt);
            completions.remember(actorId,requestKey,fingerprint,result);
            audit.record("TASK_COMPLETED","ENGINE_TASK",taskId,Map.of("recordId",recordId.toString(),"activityId",task.engine().taskDefinitionKey(),"action",command.action().name(),"recordStatus",status,"revision",result.recordRevision(),"systemResetFields",reset.stream().sorted().toList()));
            return result;
        });
    }
    private static ApplicationException forbidden(){return new ApplicationException(ErrorCode.FORBIDDEN,"Chỉ người được giao mới được thực hiện action hợp lệ của công việc.");}
}
