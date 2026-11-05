package com.x.bpm.businessrecord.config;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.audit.application.AuditEventRecorder;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class RecordModuleConfiguration {
    @Bean
    public SubmitRecordUseCase submitRecordUseCase(RecordStore store,RecordSubmissionStore submissions,RecordTransactionRunner transactions,
            CurrentActorProvider actors,FormDataService forms,com.x.bpm.task.assignment.application.AssignmentDirectoryPort directory,
            com.x.bpm.process.application.engine.ProcessEnginePort engine,RecordEngineData data,AuditEventRecorder audit,java.time.Clock clock) {
        return new SubmitRecordUseCase(store,submissions,transactions,actors,forms,directory,engine,data,audit,clock);
    }
    @Bean
    public RecordDraftUseCase recordDraftUseCase(RecordStore store,RecordTransactionRunner transactions,CurrentActorProvider actors,FormDataService forms,AuditEventRecorder audit){return new RecordDraftUseCase(store,transactions,actors,forms,audit);}
}
