package com.x.bpm.task.config;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.businessrecord.application.RecordTransactionRunner;
import com.x.bpm.businessrecord.application.RecordEngineData;
import com.x.bpm.businessrecord.application.RecordAttachmentLinks;
import com.x.bpm.businessrecord.application.AttachmentStore;
import com.x.bpm.businessrecord.application.AttachmentStorage;
import com.x.bpm.process.application.engine.ProcessEnginePort;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.task.application.*;
import com.x.bpm.task.application.engine.TaskEnginePort;
import com.x.bpm.task.assignment.application.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class TaskRuntimeConfiguration {
    @Bean public TaskInboxUseCase taskInboxUseCase(TaskRuntimeStore store,RecordTransactionRunner transactions,CurrentActorProvider actors,
            AssignmentDirectoryPort directory,AssignmentResolver resolver,TaskEnginePort engine,FormDataService forms,AuditEventRecorder audit,RecordAttachmentLinks attachments){
        return new TaskInboxUseCase(store,transactions,actors,directory,resolver,engine,forms,audit,attachments);
    }
    @Bean public CompleteTaskUseCase completeTaskUseCase(TaskRuntimeStore tasks,TaskCompletionStore completions,RecordTransactionRunner transactions,CurrentActorProvider actors,
            TaskInboxUseCase access,FormDataService forms,TaskEnginePort taskEngine,ProcessEnginePort processEngine,RecordEngineData encoding,AuditEventRecorder audit,RecordAttachmentLinks attachments){
        return new CompleteTaskUseCase(tasks,completions,transactions,actors,access,forms,taskEngine,processEngine,encoding,audit,attachments);
    }
    @Bean public TaskAttachmentUseCase taskAttachmentUseCase(TaskRuntimeStore tasks,TaskInboxUseCase access,AttachmentStore files,AttachmentStorage storage,
            RecordTransactionRunner transactions,CurrentActorProvider actors,AuditEventRecorder audit){return new TaskAttachmentUseCase(tasks,access,files,storage,transactions,actors,audit);}
}
