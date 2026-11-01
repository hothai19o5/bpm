package com.x.bpm;

import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class BpmApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProcessEngine processEngine;

    @Test
    void startsFlowableAgainstIsolatedPostgres() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).isEqualTo("bpm_test");
        }

        assertThat(processEngine.getRepositoryService().createDeploymentQuery().count())
                .isNotNegative();
    }

}
