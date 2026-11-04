package com.x.bpm.process.config;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.application.ListProcessDefinitionsUseCase;
import com.x.bpm.process.application.ProcessDefinitionQueryPort;
import com.x.bpm.process.domain.ProcessDefinitionReadPolicy;
import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.process.application.ProcessDefinitionUseCase;
import com.x.bpm.process.application.ProcessStore;
import com.x.bpm.process.application.ProcessTransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProcessModuleConfiguration {

    @Bean
    public com.x.bpm.process.application.ProcessCatalogUseCase processCatalogUseCase(com.x.bpm.process.application.ProcessCatalogStore store,CurrentActorProvider actors) {
        return new com.x.bpm.process.application.ProcessCatalogUseCase(store,actors);
    }

    @Bean
    public com.x.bpm.process.application.ProcessLifecycleUseCase processLifecycleUseCase(ProcessStore store,
            com.x.bpm.process.application.ProcessPublicationStore locks,com.x.bpm.process.application.ProcessLifecycleStore lifecycle,
            ProcessTransactionRunner transactions,CurrentActorProvider actors,AuditEventRecorder audit,com.x.bpm.process.application.ProcessValidationPort validator) {
        return new com.x.bpm.process.application.ProcessLifecycleUseCase(store,locks,lifecycle,transactions,actors,audit,validator);
    }

    @Bean
    public com.x.bpm.process.application.PublishProcessUseCase publishProcessUseCase(ProcessStore store,
            com.x.bpm.process.application.ProcessPublicationStore publications, ProcessTransactionRunner transactions,
            CurrentActorProvider actors, AuditEventRecorder audit, com.x.bpm.process.application.ProcessValidationPort validator,
            com.x.bpm.process.application.engine.ProcessEnginePort engine) {
        return new com.x.bpm.process.application.PublishProcessUseCase(store,publications,transactions,actors,audit,validator,engine);
    }

    @Bean
    public ProcessDefinitionReadPolicy processDefinitionReadPolicy() {
        return new ProcessDefinitionReadPolicy();
    }

    @Bean
    public ListProcessDefinitionsUseCase listProcessDefinitionsUseCase(
            CurrentActorProvider currentActorProvider, ProcessDefinitionReadPolicy readPolicy,
            ProcessDefinitionQueryPort queryPort) {
        return new ListProcessDefinitionsUseCase(currentActorProvider, readPolicy, queryPort);
    }

    @Bean
    public ProcessDefinitionUseCase processDefinitionUseCase(ProcessStore store, ProcessTransactionRunner transactions,
            CurrentActorProvider actors, AuditEventRecorder audit, com.x.bpm.process.application.ProcessValidationPort validator) {
        return new ProcessDefinitionUseCase(store, transactions, actors, audit, validator);
    }
}
