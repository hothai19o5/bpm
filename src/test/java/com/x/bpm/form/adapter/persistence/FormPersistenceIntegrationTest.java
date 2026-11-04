package com.x.bpm.form.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.x.bpm.form.application.CreateForm;
import com.x.bpm.form.application.FormStore;
import com.x.bpm.form.domain.FormSchemaContract;
import com.x.bpm.identity.adapter.persistence.UserEntity;
import com.x.bpm.shared.error.ApplicationException;
import com.x.bpm.support.IsolatedPostgresTest;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@IsolatedPostgresTest
class FormPersistenceIntegrationTest {
    @Autowired private FormStore forms;
    @Autowired private EntityManager entities;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ObjectMapper json;

    @Test
    @SuppressWarnings("unchecked")
    void roundTripsJsonbWithStableIdsAndRejectsMismatchedDefinitionAndDuplicateKey() throws Exception {
        Map<String, Object> fixture;
        try (var stream = getClass().getResourceAsStream("/forms/purchase-request.v1.json")) {
            fixture = json.readValue(stream, Map.class);
        }
        var schema = (Map<String, Object>) fixture.get("schema");
        var ui = (Map<String, Object>) fixture.get("uiSchema");
        new FormSchemaContract().validate(schema, ui);
        var actorId = transactions.execute(status -> {
            var user = new UserEntity();
            user.setUsername("form-test-" + UUID.randomUUID());
            user.setNormalizedUsername(user.getUsername());
            user.setDisplayName("Form Test");
            user.setPasswordHash("{noop}test-fixture-only");
            entities.persist(user);
            entities.flush();
            return user.getId();
        });
        var key = "form-" + UUID.randomUUID();
        try {
            var created = forms.create(new CreateForm(key, "Mua sắm", null, schema, ui), actorId);
            var loaded = forms.findVersion(created.definition().id(), created.version().id()).orElseThrow();
            assertThat(loaded).isEqualTo(created);
            assertThat(loaded.version().versionNumber()).isEqualTo(1);
            assertThat(loaded.version().publishedAt()).isNull();
            assertThat(loaded.version().createdBy()).isEqualTo(actorId);
            assertThat(forms.findVersion(UUID.randomUUID(), created.version().id())).isEmpty();
            assertThatThrownBy(() -> forms.create(new CreateForm(key, "Duplicate", null, schema, ui), actorId))
                    .isInstanceOf(ApplicationException.class);
        } finally {
            transactions.executeWithoutResult(status -> {
                entities.createQuery("delete from FormVersionEntity v where v.createdBy.id = :actor")
                        .setParameter("actor", actorId).executeUpdate();
                entities.createQuery("delete from FormDefinitionEntity f where f.owner.id = :actor")
                        .setParameter("actor", actorId).executeUpdate();
                entities.remove(entities.find(UserEntity.class, actorId));
            });
        }
    }
}
