package com.x.bpm.businessrecord.config;
import com.x.bpm.businessrecord.application.*;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.audit.application.AuditEventRecorder;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false)
public class RecordModuleConfiguration {
    @Bean
    public RecordDraftUseCase recordDraftUseCase(RecordStore store,RecordTransactionRunner transactions,CurrentActorProvider actors,FormDataService forms,AuditEventRecorder audit){return new RecordDraftUseCase(store,transactions,actors,forms,audit);}
}
