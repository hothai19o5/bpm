package com.x.bpm.identity.config;

import com.x.bpm.identity.domain.PasswordPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lắp ghép chính sách identity; adapter vẫn phụ thuộc vào các port phía application. */
@Configuration(proxyBeanMethods = false)
public class IdentityModuleConfiguration {
    @Bean
    public PasswordPolicy passwordPolicy() {
        return new PasswordPolicy();
    }
}
