package com.x.bpm.support;

import java.util.Arrays;
import java.util.regex.Pattern;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs after config data, before Hibernate/Flowable can create or drop any tables. */
public final class TestDatabaseEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    private static final Pattern TEST_URL = Pattern.compile(
            "jdbc:postgresql://(?:[a-zA-Z0-9.-]+|\\[[0-9a-fA-F:]+\\]):[0-9]{1,5}/bpm_test");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            validate(environment);
        }
    }

    static void validate(ConfigurableEnvironment environment) {
        if (!Arrays.equals(environment.getActiveProfiles(), new String[] {"test"})) {
            throw new IllegalStateException("Database tests must activate only the test profile");
        }
        String url = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "");
        if (!TEST_URL.matcher(url).matches() || !"bpm_test".equals(username)) {
            // Do not print URLs/credentials: a mistaken URL may contain secrets.
            throw new IllegalStateException(
                    "Database tests require jdbc:postgresql://<host>:<port>/bpm_test and user bpm_test; "
                            + "URL query parameters and dev credentials are forbidden");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
