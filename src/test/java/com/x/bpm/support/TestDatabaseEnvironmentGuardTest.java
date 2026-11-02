package com.x.bpm.support;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseEnvironmentGuardTest {
    @Test
    void allowsExplicitPostgresTestDatabase() {
        var environment = environment(
                "jdbc:postgresql://postgres:5432/bpm_test?currentSchema=flowable,business,public",
                "bpm_test");
        assertThatCode(() -> TestDatabaseEnvironmentGuard.validate(environment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://postgres:5432/bpm",
            "jdbc:postgresql://postgres:5432/bpm_test",
            "jdbc:postgresql://postgres:5432/bpm_test?currentSchema=public",
            "jdbc:postgresql://postgres:5432/bpm_test?currentSchema=business,flowable,public",
            "jdbc:postgresql://postgres:5432/bpm_test?user=bpm",
            "jdbc:postgresql://postgres:5432/bpm_test/other",
            "jdbc:h2:mem:bpm_test", ""
    })
    void rejectsDevDatabaseAndAlternateConnectionOptions(String url) {
        assertThatThrownBy(() -> TestDatabaseEnvironmentGuard.validate(environment(url, "bpm_test")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDevUserAndMixedProfiles() {
        assertThatThrownBy(() -> TestDatabaseEnvironmentGuard.validate(
                environment("jdbc:postgresql://localhost:5432/bpm_test"
                        + "?currentSchema=flowable,business,public", "bpm")))
                .isInstanceOf(IllegalStateException.class);
        var environment = environment("jdbc:postgresql://localhost:5432/bpm_test"
                + "?currentSchema=flowable,business,public", "bpm_test");
        environment.setActiveProfiles("test", "dev");
        assertThatThrownBy(() -> TestDatabaseEnvironmentGuard.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    private MockEnvironment environment(String url, String user) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", url, "spring.datasource.username", user)));
        return environment;
    }
}
