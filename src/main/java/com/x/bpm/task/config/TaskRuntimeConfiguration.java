package com.x.bpm.task.config;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.businessrecord.application.RecordTransactionRunner;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.task.application.*;
import com.x.bpm.task.application.engine.TaskEnginePort;
import com.x.bpm.task.assignment.application.*;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class TaskRuntimeConfiguration {
    @Bean public TaskInboxUseCase taskInboxUseCase(TaskRuntimeStore store,RecordTransactionRunner transactions,CurrentActorProvider actors,
            AssignmentDirectoryPort directory,AssignmentResolver resolver,TaskEnginePort engine,FormDataService forms,AuditEventRecorder audit){
        return new TaskInboxUseCase(store,transactions,actors,directory,resolver,engine,forms,audit);
    }
}
