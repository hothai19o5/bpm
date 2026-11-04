package com.x.bpm.form.config;

import com.x.bpm.audit.application.AuditEventRecorder;
import com.x.bpm.authorization.domain.AuthorizationPolicy;
import com.x.bpm.form.application.FormDefinitionUseCase;
import com.x.bpm.form.application.FormDataService;
import com.x.bpm.form.application.FormStore;
import com.x.bpm.form.application.FormTransactionRunner;
import com.x.bpm.identity.application.CurrentActorProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FormModuleConfiguration {
    @Bean
    public FormDataService formDataService() {
        return new FormDataService();
    }

    @Bean
    public FormDefinitionUseCase formDefinitionUseCase(FormStore store,
            FormTransactionRunner transactions, CurrentActorProvider actors,
            AuditEventRecorder audit) {
        return new FormDefinitionUseCase(store, transactions, actors, new AuthorizationPolicy(), audit);
    }
}
