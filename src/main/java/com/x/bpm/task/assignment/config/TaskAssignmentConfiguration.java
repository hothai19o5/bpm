package com.x.bpm.task.assignment.config;

import com.x.bpm.task.assignment.application.AssignmentDirectoryPort;
import com.x.bpm.task.assignment.application.AssignmentResolver;
import com.x.bpm.task.assignment.application.*;
import com.x.bpm.task.assignment.adapter.flowable.TaskCreatedAssignmentListener;
import com.x.bpm.identity.application.CurrentActorProvider;
import java.util.ArrayList;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskAssignmentConfiguration {
    @Bean
    public AssignmentResolver assignmentResolver(AssignmentDirectoryPort directory) {
        return new AssignmentResolver(directory);
    }
    @Bean
    public RuntimeAssignmentUseCase runtimeAssignmentUseCase(RuntimeAssignmentStore store, AssignmentResolver resolver, AssignmentDirectoryPort directory) {
        return new RuntimeAssignmentUseCase(store, resolver, directory);
    }
    @Bean
    public AssignmentExceptionsUseCase assignmentExceptionsUseCase(CurrentActorProvider actors, RuntimeAssignmentStore store) {
        return new AssignmentExceptionsUseCase(actors, store);
    }
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> assignmentEngineConfigurer(RuntimeAssignmentUseCase useCase) {
        return configuration -> {
            var listeners = new ArrayList<org.flowable.common.engine.api.delegate.event.FlowableEventListener>();
            if (configuration.getEventListeners() != null) listeners.addAll(configuration.getEventListeners());
            listeners.add(new TaskCreatedAssignmentListener(useCase));
            configuration.setEventListeners(listeners);
        };
    }
}
