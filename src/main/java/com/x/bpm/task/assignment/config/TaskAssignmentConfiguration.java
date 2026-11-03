package com.x.bpm.task.assignment.config;

import com.x.bpm.task.assignment.application.AssignmentDirectoryPort;
import com.x.bpm.task.assignment.application.AssignmentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TaskAssignmentConfiguration {
    @Bean
    public AssignmentResolver assignmentResolver(AssignmentDirectoryPort directory) {
        return new AssignmentResolver(directory);
    }
}
