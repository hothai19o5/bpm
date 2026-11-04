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
            CurrentActorProvider actors, AuditEventRecorder audit) {
        return new ProcessDefinitionUseCase(store, transactions, actors, audit);
    }
}
