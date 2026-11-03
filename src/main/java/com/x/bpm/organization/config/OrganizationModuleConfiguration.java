package com.x.bpm.organization.config;

import com.x.bpm.organization.application.OrganizationManagementUseCase;
import com.x.bpm.organization.application.OrganizationPort;
import com.x.bpm.organization.application.OrganizationTransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrganizationModuleConfiguration {
    @Bean
    public OrganizationManagementUseCase organizationManagementUseCase(
            OrganizationPort organizations, OrganizationTransactionRunner transactions) {
        return new OrganizationManagementUseCase(organizations, transactions);
    }
}
