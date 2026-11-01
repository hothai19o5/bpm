package com.x.bpm.process.config;

import com.x.bpm.identity.application.CurrentActorProvider;
import com.x.bpm.process.application.ListProcessDefinitionsUseCase;
import com.x.bpm.process.application.ProcessDefinitionQueryPort;
import com.x.bpm.process.domain.ProcessDefinitionReadPolicy;

/**
 * Nơi lắp ghép module; CHƯA là @Configuration, chưa có @Bean.
 * Đợi identity principal và persistence adapter thật trước khi bật endpoint mẫu.
 */
public class ProcessModuleConfiguration {

    // TODO: @Bean cùng lúc kích hoạt module sau khi có adapter identity/persistence thật.
    public ProcessDefinitionReadPolicy processDefinitionReadPolicy() {
        return new ProcessDefinitionReadPolicy();
    }

    // TODO: @Bean khi đã có CurrentActorProvider và ProcessDefinitionQueryPort thật.
    public ListProcessDefinitionsUseCase listProcessDefinitionsUseCase(
            CurrentActorProvider currentActorProvider, ProcessDefinitionReadPolicy readPolicy,
            ProcessDefinitionQueryPort queryPort) {
        return new ListProcessDefinitionsUseCase(currentActorProvider, readPolicy, queryPort);
    }
}
