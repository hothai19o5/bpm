package com.x.bpm;

import com.x.bpm.support.IsolatedPostgresTest;
import com.x.bpm.support.persistence.SchemaProbe;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@IsolatedPostgresTest
class SchemaInitializationTest {
    @Autowired private EntityManager entityManager;

    @Test
    @Transactional
    @Sql("/fixtures/schema-probe.sql")
    void createsJpaSchemaAndLoadsRepeatableSeedOnPostgres() {
        assertThat(entityManager.find(SchemaProbe.class, "p14-seed").getLabel())
                .isEqualTo("isolated-postgres-fixture");
    }
}
