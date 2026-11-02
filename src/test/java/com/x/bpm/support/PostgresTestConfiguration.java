package com.x.bpm.support;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {
    @Bean
    static BeanPostProcessor isolatedDatabaseGuard(ConfigurableEnvironment environment) {
        // Check again after test property overrides have been applied.
        TestDatabaseEnvironmentGuard.validate(environment);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource) {
                    try (var connection = dataSource.getConnection()) {
                        var metadata = connection.getMetaData();
                        if (!"PostgreSQL".equals(metadata.getDatabaseProductName())
                                || !"bpm_test".equals(connection.getCatalog())
                                || !"bpm_test".equals(metadata.getUserName())) {
                            throw new IllegalStateException("Connected database is not the isolated bpm_test database");
                        }
                    } catch (SQLException exception) {
                        throw new IllegalStateException("Cannot verify isolated PostgreSQL test connection", exception);
                    }
                }
                return bean;
            }
        };
    }
}
